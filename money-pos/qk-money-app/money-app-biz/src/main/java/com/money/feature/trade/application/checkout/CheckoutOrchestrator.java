package com.money.feature.trade.application.checkout;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderLog;
import com.money.feature.trade.domain.order.OmsOrderLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 🌟 结算大总管 (Orchestrator)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutOrchestrator {

    private final CheckoutValidationService validationService;
    private final CheckoutPricingService pricingService;
    private final CheckoutOrderService orderService;
    private final CheckoutInventoryService inventoryService;
    private final CheckoutMemberAssetService memberAssetService;
    private final CheckoutPaymentService paymentService;
    private final OmsOrderLogService omsOrderLogService;

    @Transactional(rollbackFor = Exception.class)
    public SettleResultVO orchestrate(SettleAccountsDTO request) {
        log.info("🚀 启动结算流水线，单号: {}", request.getReqId());

        CheckoutContext context = new CheckoutContext();
        context.setRequest(request);

        // ================= 🌟 1. 幂等拦截闸口 =================
        if (orderService.loadExistingOrder(context)) {
            paymentService.loadExistingPayments(context);

            log.info("【幂等拦截生效】订单 {} 已成功处理过，已100%无损还原原始结账快照并放行。", context.getOrder().getOrderNo());
            return buildFinalResult(context);
        }

        // ================= 🌟 2. 正常流水线 =================
        validationService.validate(context);
        pricingService.calculate(context);

        // 🌟 P0 核心修复 2：粮草先行！必须先将前端支付参数解析归一化，再谈后续的扣款
        paymentService.preProcessPayments(context);

        orderService.createOrder(context);
        inventoryService.deductStock(context);

        // 此时，资产管家能够拿到 100% 准确的 paymentResult 对象了
        memberAssetService.handleAsset(context);

        paymentService.handlePayment(context);

        saveAuditLog(context);

        return buildFinalResult(context);
    }

    private void saveAuditLog(CheckoutContext context) {
        OmsOrder order = context.getOrder();
        NormalizedPaymentResult payResult = context.getPaymentResult();

        int totalItemCount = context.getOrderDetails().stream().mapToInt(d -> d.getQuantity()).sum();

        // 安全获取，防止支付方式为空
        String distinctPayMethods = "";
        if (payResult != null && payResult.getValidItems() != null) {
            distinctPayMethods = payResult.getValidItems().stream()
                    .map(p -> StrUtil.isNotBlank(p.getPayTag()) ? p.getMethodCode() + ":" + p.getPayTag() : p.getMethodCode())
                    .distinct()
                    .collect(Collectors.joining(","));
        }

        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(order.getId());

        Map<String, Object> auditMap = new LinkedHashMap<>();
        auditMap.put("action", "SETTLE_SUCCESS");
        auditMap.put("orderNo", order.getOrderNo());
        auditMap.put("memberId", context.getRequest().getMember());
        auditMap.put("itemCount", totalItemCount);
        auditMap.put("detailCount", context.getOrderDetails().size());
        auditMap.put("payMethods", distinctPayMethods);
        auditMap.put("finalPay", order.getPayAmount());

        if (payResult != null) {
            auditMap.put("totalPaid", payResult.getTotalPaid());
            auditMap.put("change", payResult.getChangeAmount());
            auditMap.put("net", payResult.getNetReceived());
        }

        orderLog.setDescription(JSONUtil.toJsonStr(auditMap));
        omsOrderLogService.save(orderLog);
    }

    private SettleResultVO buildFinalResult(CheckoutContext context) {
        OmsOrder order = context.getOrder();
        NormalizedPaymentResult payResult = context.getPaymentResult();

        SettleResultVO vo = new SettleResultVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setFinalPayAmount(order.getPayAmount());

        if (payResult != null) {
            vo.setTotalPaid(payResult.getTotalPaid());
            vo.setChangeAmount(payResult.getChangeAmount());
            vo.setNetReceived(payResult.getNetReceived());
        }

        vo.setPaymentTime(order.getPaymentTime());
        vo.setMemberName(order.getMember());
        vo.setCouponDeduct(order.getCouponAmount());
        vo.setVoucherDeduct(order.getUseVoucherAmount());
        vo.setManualDeduct(order.getManualDiscountAmount());

        return vo;
    }
}
