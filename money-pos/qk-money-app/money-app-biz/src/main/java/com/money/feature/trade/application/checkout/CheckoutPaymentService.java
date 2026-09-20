package com.money.feature.trade.application.checkout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.SettleAccountsDTO; // 🌟 引入 DTO
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderPay;
import com.money.mapper.OmsOrderPayMapper;
import com.money.service.SysDictDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🌟 结算流水线第六关：出纳员
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    private final OmsOrderPayMapper omsOrderPayMapper;
    private final SysDictDetailService sysDictDetailService;

    // ==========================================
    // 🌟 P0 核心修复 1：支付数据入口归一化 (防大小写/空格黑客)
    // ==========================================
    public void preProcessPayments(CheckoutContext context) {
        SettleAccountsDTO request = context.getRequest();
        NormalizedPaymentResult result = new NormalizedPaymentResult();

        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;

        if (request.getPayments() != null) {
            for (SettleAccountsDTO.PaymentItem p : request.getPayments()) {
                if (p.getPayAmount() == null || p.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // 过滤无效支付项
                }

                NormalizedPaymentResult.StandardPayItem item = new NormalizedPaymentResult.StandardPayItem();

                // 🌟 核心防御：强制 Trim + 大写，将前端的不规范数据转换为系统标准口径
                String safeCode = StringUtils.hasText(p.getPayMethodCode()) ? p.getPayMethodCode().trim().toUpperCase() : "UNKNOWN";

                item.setMethodCode(safeCode);
                item.setPayTag(p.getPayTag());

                // 暂时将实收和净额保持一致（找零逻辑后续可在此处扩展）
                item.setNetAmount(p.getPayAmount());
                item.setOriginalAmount(p.getPayAmount());
                item.setChangeAmount(BigDecimal.ZERO);

                result.getValidItems().add(item);
                netTotal = netTotal.add(p.getPayAmount());
                originalTotal = originalTotal.add(p.getPayAmount());
            }
        }

        result.setNetReceived(netTotal);
        result.setTotalPaid(originalTotal);
        result.setChangeAmount(BigDecimal.ZERO); // 此处初始化找零池

        // 🌟 提前挂载到上下文，供后续的“资产扣除”关卡使用
        context.setPaymentResult(result);
    }

    public void loadExistingPayments(CheckoutContext context) {
        String orderNo = context.getOrder().getOrderNo();
        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(
                new LambdaQueryWrapper<OmsOrderPay>().eq(OmsOrderPay::getOrderNo, orderNo)
        );

        NormalizedPaymentResult result = new NormalizedPaymentResult();
        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal changeTotal = BigDecimal.ZERO;

        for (OmsOrderPay pay : pays) {
            NormalizedPaymentResult.StandardPayItem item = new NormalizedPaymentResult.StandardPayItem();
            item.setMethodCode(pay.getPayMethodCode());
            item.setMethodName(pay.getPayMethodName());
            item.setPayTag(pay.getPayTag());

            BigDecimal net = pay.getNetAmount() != null ? pay.getNetAmount() : pay.getPayAmount();
            BigDecimal orig = pay.getOriginalAmount() != null ? pay.getOriginalAmount() : net;
            BigDecimal change = pay.getChangeAllocated() != null ? pay.getChangeAllocated() : BigDecimal.ZERO;

            item.setNetAmount(net);
            item.setOriginalAmount(orig);
            item.setChangeAmount(change);

            result.getValidItems().add(item);
            netTotal = netTotal.add(net);
            originalTotal = originalTotal.add(orig);
            changeTotal = changeTotal.add(change);
        }

        result.setNetReceived(netTotal);
        result.setTotalPaid(originalTotal);
        result.setChangeAmount(changeTotal);
        context.setPaymentResult(result);
    }

    public void handlePayment(CheckoutContext context) {
        NormalizedPaymentResult payResult = context.getPaymentResult();
        String orderNo = context.getOrder().getOrderNo();
        LocalDateTime now = LocalDateTime.now();

        // 防止 payResult 为空（加固保护）
        if (payResult == null || payResult.getValidItems() == null) return;

        for (NormalizedPaymentResult.StandardPayItem item : payResult.getValidItems()) {
            if ((item.getNetAmount() == null || item.getNetAmount().compareTo(BigDecimal.ZERO) == 0) &&
                    (item.getOriginalAmount() == null || item.getOriginalAmount().compareTo(BigDecimal.ZERO) == 0)) {
                continue;
            }

            OmsOrderPay payRecord = new OmsOrderPay();
            payRecord.setOrderNo(orderNo);
            payRecord.setPayMethodCode(item.getMethodCode());
            payRecord.setPayTag(item.getPayTag());
            payRecord.setPayMethodName(getSafeMethodName(item.getMethodCode(), item.getPayTag()));

            payRecord.setPayAmount(item.getNetAmount());
            payRecord.setOriginalAmount(item.getOriginalAmount());
            payRecord.setNetAmount(item.getNetAmount());
            payRecord.setChangeAllocated(item.getChangeAmount() != null ? item.getChangeAmount() : BigDecimal.ZERO);
            payRecord.setCreateTime(now);

            omsOrderPayMapper.insert(payRecord);
        }
    }

    private String getSafeMethodName(String methodCode, String payTag) {
        try {
            if (StringUtils.hasText(payTag)) {
                String subMethodName = findDictionaryName("paySubTag", payTag);
                if (subMethodName != null) return subMethodName;
            }
            if (StringUtils.hasText(methodCode)) {
                String mainMethodName = findDictionaryName("pos_payment_method", methodCode);
                if (mainMethodName != null) return mainMethodName;
            }
        } catch (Exception e) {
            log.error("💥 动态匹配支付方式字典失败，降级到安全兜底模式。Code:{}, Tag:{}", methodCode, payTag, e);
        }
        String code = StringUtils.hasText(payTag) ? payTag : methodCode;
        return "其他渠道(" + code + ")";
    }

    private String findDictionaryName(String dict, String value) {
        for (Map.Entry<String, String> entry : sysDictDetailService.getValueToCnDescMap(dict).entrySet()) {
            if (value.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }
}
