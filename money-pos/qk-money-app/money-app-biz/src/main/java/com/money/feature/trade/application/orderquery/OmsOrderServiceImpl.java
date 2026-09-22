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
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderLog;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderPay;
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
     * Loads the canonical order-status dictionary, retaining the legacy underscore
     * lookup only for databases created before the canonical key was established.
     */
    private Map<String, String> getOrderStatusDictMap() {
        Map<String, String> dictMap = new HashMap<>();
        try {
            Map<String, String> details = sysDictDetailService.getValueToCnDescMap("orderStatus");

            if (details.isEmpty()) {
                details = sysDictDetailService.getValueToCnDescMap("order_status");
            }

            if (!details.isEmpty()) {
                for (Map.Entry<String, String> detail : details.entrySet()) {
                    if (StrUtil.isNotBlank(detail.getKey()) && StrUtil.isNotBlank(detail.getValue())) {
                        String safeKey = detail.getKey().trim().toUpperCase();
                        dictMap.put(safeKey, detail.getValue());
                    }
                }
            } else {
                log.error("订单状态字典 [orderStatus] 不存在，订单状态将使用枚举兜底。");
            }
        } catch (Exception e) {
            log.error("字典查询发生异常: ", e);
        }
        return dictMap;
    }

    private String translateStatusDesc(String statusCode, Map<String, String> dictMap) {
        if (statusCode == null) return "-";

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
