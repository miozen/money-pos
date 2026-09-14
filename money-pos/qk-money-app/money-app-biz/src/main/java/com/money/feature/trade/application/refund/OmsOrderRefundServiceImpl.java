package com.money.feature.trade.application.refund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.BizErrorStatus;
import com.money.constant.OrderStatusEnum;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.feature.trade.domain.order.OmsOrderLogService;
import com.money.feature.trade.application.refund.OmsOrderRefundService;
import com.money.feature.trade.application.checkout.refund.RefundAssetHelper;
import com.money.feature.trade.application.checkout.refund.RefundInventoryHelper;
import com.money.feature.trade.application.checkout.refund.RefundStateGuard;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🌟 V9.0 极简编排版：OmsOrderRefundServiceImpl
 * 仅负责状态流转统筹，脏活累活均已下放至 Helper
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OmsOrderRefundServiceImpl implements OmsOrderRefundService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final OmsOrderLogService omsOrderLogService;

    // 🌟 核心：引入三个专门的部门经理
    private final RefundStateGuard stateGuard;
    private final RefundInventoryHelper inventoryHelper;
    private final RefundAssetHelper assetHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnOrder(String reqId, String orderNo) {
        // 1. 防线拦截
        stateGuard.acquireIdempotent(reqId, "FULL_REFUND");
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, orderNo));
        stateGuard.checkRefundableState(order);

        // 2. 行级锁双保险
        boolean isLocked = omsOrderMapper.update(null, new LambdaUpdateWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo)
                .ne(OmsOrder::getStatus, OrderStatusEnum.REFUNDED.name())
                .set(OmsOrder::getUpdateTime, LocalDateTime.now())) > 0;
        if (!isLocked) throw new BaseException(BizErrorStatus.POS_REFUND_REPEAT).withData(orderNo);

        // 3. 更新主订单状态
        omsOrderMapper.updateRefundStatusToFull(orderNo, OrderStatusEnum.REFUNDED.name());

        // 4. 委托【仓储部】处理明细库存
        List<OmsOrderDetail> details = omsOrderDetailMapper.selectList(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, orderNo));
        inventoryHelper.processFullOrderInventory(orderNo, details);

        // 5. 委托【财务部】处理资产与退款
        assetHelper.processFullOrderAsset(order, orderNo);

        // 6. 记录订单生命周期日志
        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("执行整单退款操作，资产与满减券已原路回退");
        omsOrderLogService.save(orderLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnGoods(ReturnGoodsDTO dto) {
        // 1. 防线拦截
        stateGuard.acquireIdempotent(dto.getReqId(), "PARTIAL_REFUND");
        OmsOrder order = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, dto.getOrderNo()));
        stateGuard.checkRefundableState(order);

        // 2. 校验明细归属
        OmsOrderDetail detail = omsOrderDetailMapper.selectById(dto.getDetailId());
        if (detail == null || !detail.getOrderNo().equals(order.getOrderNo())) {
            throw new BaseException(BizErrorStatus.POS_REFUND_NOT_FOUND).withData(dto.getOrderNo());
        }

        // 3. 金额沙盘推演：计算需要退的款和券
        BigDecimal unitSalesPrice = detail.getGoodsPrice() != null ? detail.getGoodsPrice() : BigDecimal.ZERO;
        BigDecimal unitCostPrice = detail.getPurchasePrice() != null ? detail.getPurchasePrice() : BigDecimal.ZERO;
        BigDecimal totalDetailCoupon = detail.getCoupon() != null ? detail.getCoupon() : BigDecimal.ZERO;

        if (order.getCouponAmount() == null || order.getCouponAmount().compareTo(BigDecimal.ZERO) <= 0) {
            totalDetailCoupon = BigDecimal.ZERO; // 免券单强制归零
        }
        BigDecimal unitCoupon = (detail.getQuantity() > 0 && totalDetailCoupon.compareTo(BigDecimal.ZERO) > 0)
                ? totalDetailCoupon.divide(new BigDecimal(detail.getQuantity()), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal refundSales = unitSalesPrice.multiply(new BigDecimal(dto.getReturnQty()));
        BigDecimal refundCost = unitCostPrice.multiply(new BigDecimal(dto.getReturnQty()));
        BigDecimal refundMemberCoupon = unitCoupon.multiply(new BigDecimal(dto.getReturnQty()));

        // 4. 委托【仓储部】处理单品库存回补
        inventoryHelper.processPartialReturnInventory(dto.getOrderNo(), detail, dto.getReturnQty());

        // 5. 更新订单数据库金额累加字段
        omsOrderMapper.applyPartialRefund(dto.getOrderNo(), refundSales, refundCost, refundMemberCoupon, OrderStatusEnum.PARTIAL_REFUNDED.name());

        // 6. 委托【财务部】处理会员资产回退
        assetHelper.processPartialReturnAsset(order, refundSales, refundMemberCoupon, dto.getOrderNo());

        // 7. 检查是否达到全退条件升级状态
        omsOrderMapper.checkAndUpgradeToFullRefund(dto.getOrderNo());

        // 8. 记录订单生命周期日志
        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setDescription("执行部分退货: [" + detail.getGoodsName() + "] x" + dto.getReturnQty() + "，按商品成交价直退");
        omsOrderLogService.save(orderLog);
    }
}