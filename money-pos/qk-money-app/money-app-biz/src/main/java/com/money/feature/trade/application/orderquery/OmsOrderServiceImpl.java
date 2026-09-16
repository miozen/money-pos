package com.money.feature.trade.application.orderquery;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.constant.BizErrorStatus;
import com.money.constant.OrderStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.contract.member.MemberOrderProfileQuery;
import com.money.contract.member.MemberOrderProfileSnapshot;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.entity.*;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.feature.trade.domain.order.OmsOrderDetailService;
import com.money.feature.trade.domain.order.OmsOrderLogService;
import com.money.feature.trade.application.orderquery.OmsOrderService;
import com.money.service.SysDictDetailService;
import com.money.util.PageUtil;
import com.money.web.exception.BaseException;
import com.money.web.util.BeanMapUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmsOrderServiceImpl extends ServiceImpl<OmsOrderMapper, OmsOrder> implements OmsOrderService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderLogService omsOrderLogService;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final MemberOrderProfileQuery memberOrderProfileQuery;

    private final SysDictDetailService sysDictDetailService;

    /**
     * 🌟 暴力兼容版字典加载器 (带上帝视角日志)
     */
    private Map<String, String> getOrderStatusDictMap() {
        Map<String, String> dictMap = new HashMap<>();
        try {
            log.info("====== 🕵️ 开始查杀字典问题 ======");
            // 1. 尝试查询驼峰命名
            List<SysDictDetail> details = sysDictDetailService.listByDict("orderStatus");

            // 2. 如果查不到，尝试查询下划线命名 (数据库常见命名规范)
            if (details == null || details.isEmpty()) {
                log.warn("未查到名为 [orderStatus] 的字典，尝试查询 [order_status]...");
                details = sysDictDetailService.listByDict("order_status");
            }

            if (details != null && !details.isEmpty()) {
                for (SysDictDetail detail : details) {
                    if (StrUtil.isNotBlank(detail.getValue()) && StrUtil.isNotBlank(detail.getCnDesc())) {
                        // 🌟 核心防坑：将 key 强制转为大写并去掉前后空格
                        String safeKey = detail.getValue().trim().toUpperCase();
                        dictMap.put(safeKey, detail.getCnDesc());
                    }
                }
                log.info("✅ 成功加载字典映射表 (已统一转大写): {}", dictMap);
            } else {
                log.error("❌ 数据库中根本不存在 [orderStatus] 或 [order_status] 的字典配置！");
            }
            log.info("====================================");
        } catch (Exception e) {
            log.error("字典查询发生异常: ", e);
        }
        return dictMap;
    }

    /**
     * 🌟 暴力兼容版语义翻译器
     */
    private String translateStatusDesc(String statusCode, Map<String, String> dictMap) {
        if (statusCode == null) return "-";

        // 🌟 核心防坑：将传进来的订单状态也强制转大写去空格
        String safeStatusCode = statusCode.trim().toUpperCase();

        if (dictMap != null && dictMap.containsKey(safeStatusCode)) {
            return dictMap.get(safeStatusCode);
        }

        log.warn("⚠️ 字典表里找不到状态 [{}] 的配置，已退化使用枚举兜底！", safeStatusCode);
        return OrderStatusEnum.getFallbackDesc(statusCode);
    }

    @Override
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        String memberKeyword = queryDTO.getMember() != null ? String.valueOf(queryDTO.getMember()) : null;
        Page<OmsOrder> page = omsOrderMapper.selectPage(PageUtil.toPage(queryDTO), new LambdaQueryWrapper<OmsOrder>()
                .and(StrUtil.isNotBlank(memberKeyword), w -> w.like(OmsOrder::getMember, memberKeyword).or().like(OmsOrder::getContact, memberKeyword))
                .like(StrUtil.isNotBlank(queryDTO.getOrderNo()), OmsOrder::getOrderNo, queryDTO.getOrderNo())
                .eq(StrUtil.isNotBlank(queryDTO.getStatus()), OmsOrder::getStatus, queryDTO.getStatus())
                .between(queryDTO.getStartTime() != null && queryDTO.getEndTime() != null, OmsOrder::getCreateTime, queryDTO.getStartTime(), queryDTO.getEndTime())
                .orderByDesc(OmsOrder::getCreateTime));

        PageVO<OmsOrderVO> pageVO = PageUtil.toPageVO(page, OmsOrderVO::new);

        if (pageVO.getRecords() != null && !pageVO.getRecords().isEmpty()) {
            Map<String, String> dictMap = getOrderStatusDictMap();
            pageVO.getRecords().forEach(vo ->
                    vo.setStatusDesc(translateStatusDesc(vo.getStatus(), dictMap))
            );
        }
        return pageVO;
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        OmsOrder order = omsOrderMapper.selectById(id);
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "订单不存在");
        return assembleOrderDetail(order);
    }

    @Override
    public OrderDetailVO getOrderDetailByNo(String orderNo) {
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, orderNo));
        if (order == null) throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "订单不存在");
        return assembleOrderDetail(order);
    }

    private OrderDetailVO assembleOrderDetail(OmsOrder order) {
        OrderDetailVO vo = BeanMapUtil.to(order, OrderDetailVO::new);

        Map<String, String> dictMap = getOrderStatusDictMap();
        vo.setStatusDesc(translateStatusDesc(order.getStatus(), dictMap));

        List<OmsOrderDetail> details = omsOrderDetailService.list(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, order.getOrderNo()));
        vo.setOrderDetails(BeanMapUtil.to(details, OmsOrderDetailVO::new));

        // ✅ 替换为：直接调用我们刚刚武装好的全能档案接口！
        if (order.getMemberId() != null) {
            try {
                MemberOrderProfileSnapshot memberProfile = memberOrderProfileQuery.findByMemberId(order.getMemberId());
                vo.setMemberInfo(memberProfile);
            } catch (Exception e) {
                log.warn("获取订单关联会员详情失败: {}", e.getMessage());
            }
        }

        List<OmsOrderLog> logs = omsOrderLogService.list(new LambdaQueryWrapper<OmsOrderLog>().eq(OmsOrderLog::getOrderId, order.getId()).orderByAsc(OmsOrderLog::getCreateTime));
        vo.setOrderLog(BeanMapUtil.to(logs, OrderDetailVO.OrderLogVO::new));

        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, order.getOrderNo()));
        vo.setPayments(BeanMapUtil.to(pays, OrderDetailVO.OrderPayVO::new));

        BigDecimal balanceAmount = BigDecimal.ZERO;
        BigDecimal scanAmount = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal changeAmount = BigDecimal.ZERO;

        for (OmsOrderPay pay : pays) {
            PayMethodEnum method = PayMethodEnum.fromCode(pay.getPayMethodCode());
            if (method == null) method = PayMethodEnum.AGGREGATE;

            BigDecimal orig = pay.getOriginalAmount() != null ? pay.getOriginalAmount() : pay.getPayAmount();

            if (method == PayMethodEnum.BALANCE) balanceAmount = balanceAmount.add(orig);
            else if (method == PayMethodEnum.AGGREGATE) scanAmount = scanAmount.add(orig);
            else if (method == PayMethodEnum.CASH) cashAmount = cashAmount.add(orig);

            if (pay.getChangeAllocated() != null) {
                changeAmount = changeAmount.add(pay.getChangeAllocated());
            }
        }

        vo.setBalanceAmount(balanceAmount);
        vo.setScanAmount(scanAmount);
        vo.setCashAmount(cashAmount);
        vo.setChangeAmount(changeAmount);

        return vo;
    }
}
