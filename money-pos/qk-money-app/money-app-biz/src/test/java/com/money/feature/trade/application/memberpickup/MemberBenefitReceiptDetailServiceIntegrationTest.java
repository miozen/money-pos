package com.money.feature.trade.application.memberpickup;

import com.money.dto.memberbenefit.MemberBenefitReceiptDetailVO;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberAmountReceipt;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberAmountReceiptPay;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetReceipt;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetReceiptPay;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberAmountReceiptMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberAmountReceiptPayMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetReceiptMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetReceiptPayMapper;
import com.money.web.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MemberBenefitReceiptDetailServiceIntegrationTest {
    @Autowired private MemberBenefitReceiptDetailService service;
    @Autowired private OmsMemberAmountReceiptMapper amountReceiptMapper;
    @Autowired private OmsMemberAmountReceiptPayMapper amountPayMapper;
    @Autowired private OmsMemberTargetReceiptMapper targetReceiptMapper;
    @Autowired private OmsMemberTargetReceiptPayMapper targetPayMapper;

    @Test void returnsOnlyTheRequestedAmountReceiptAndItsPaymentSnapshot() {
        String suffix = Long.toString(System.nanoTime(), 36); String receiptNo = "MAR-" + suffix;
        OmsMemberAmountReceipt receipt = new OmsMemberAmountReceipt();
        receipt.setReceiptNo(receiptNo); receipt.setRequestNo("REQ-" + suffix); receipt.setReceiptType("AMOUNT_PACKAGE_PURCHASE");
        receipt.setMemberId(92001L); receipt.setBrandId("B-" + suffix); receipt.setAmountRightId(77L);
        receipt.setTotalAmount(new BigDecimal("100.00")); receipt.setRightDeductAmount(BigDecimal.ZERO); receipt.setSupplementAmount(new BigDecimal("100.00")); receipt.setCostAmount(BigDecimal.ZERO); receipt.setStatus("COMPLETED"); amountReceiptMapper.insert(receipt);
        OmsMemberAmountReceiptPay pay = new OmsMemberAmountReceiptPay(); pay.setReceiptNo(receiptNo); pay.setPayMethodCode("CASH"); pay.setPayMethodName("现金"); pay.setPayAmount(new BigDecimal("100.00")); pay.setOriginalAmount(new BigDecimal("100.00")); pay.setNetAmount(new BigDecimal("100.00")); pay.setChangeAllocated(BigDecimal.ZERO); amountPayMapper.insert(pay);

        MemberBenefitReceiptDetailVO found = service.detail("AMOUNT", receiptNo);

        assertThat(found).extracting(MemberBenefitReceiptDetailVO::getReceiptKind, MemberBenefitReceiptDetailVO::getReceiptNo,
                MemberBenefitReceiptDetailVO::getRightOrPlanId, MemberBenefitReceiptDetailVO::getSupplementAmount)
                .containsExactly("AMOUNT", receiptNo, 77L, new BigDecimal("100.00"));
        assertThat(found.getPayments()).singleElement().extracting(MemberBenefitReceiptDetailVO.Payment::getPayMethodName,
                MemberBenefitReceiptDetailVO.Payment::getAmount).containsExactly("现金", new BigDecimal("100.00"));
    }

    @Test void returnsTargetReasonAndRejectsUnknownKindOrReceipt() {
        String suffix = Long.toString(System.nanoTime(), 36); String receiptNo = "MTR-" + suffix;
        OmsMemberTargetReceipt receipt = new OmsMemberTargetReceipt(); receipt.setReceiptNo(receiptNo); receipt.setRequestNo("REQ-" + suffix);
        receipt.setTargetPlanId(88L); receipt.setMemberId(92002L); receipt.setReceiptType("WAIVER"); receipt.setAmount(new BigDecimal("50.00")); receipt.setStatus("COMPLETED"); receipt.setReason("活动豁免"); targetReceiptMapper.insert(receipt);
        OmsMemberTargetReceiptPay pay = new OmsMemberTargetReceiptPay(); pay.setReceiptNo(receiptNo); pay.setPayMethodCode("BALANCE"); pay.setPayMethodName("会员余额"); pay.setPayAmount(new BigDecimal("50.00")); targetPayMapper.insert(pay);

        MemberBenefitReceiptDetailVO found = service.detail("TARGET", receiptNo);

        assertThat(found).extracting(MemberBenefitReceiptDetailVO::getReceiptKind, MemberBenefitReceiptDetailVO::getRightOrPlanId,
                MemberBenefitReceiptDetailVO::getReason, MemberBenefitReceiptDetailVO::getSupplementAmount)
                .containsExactly("TARGET", 88L, "活动豁免", new BigDecimal("50.00"));
        assertThat(found.getPayments()).singleElement().extracting(MemberBenefitReceiptDetailVO.Payment::getPayMethodName).isEqualTo("会员余额");
        assertThatThrownBy(() -> service.detail("ORDER", receiptNo)).isInstanceOf(BaseException.class).hasMessageContaining("类型不合法");
        assertThatThrownBy(() -> service.detail("TARGET", "MTR-UNKNOWN")).isInstanceOf(BaseException.class).hasMessageContaining("不存在");
    }
}
