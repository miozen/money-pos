package com.money.feature.trade.application.memberpickup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/** TRADE-owned, read-only MAR/MTR receipt projection. It intentionally has no printing path. */
@Service
@RequiredArgsConstructor
public class MemberBenefitReceiptDetailService {
    private final OmsMemberAmountReceiptMapper amountReceiptMapper;
    private final OmsMemberAmountReceiptPayMapper amountPayMapper;
    private final OmsMemberTargetReceiptMapper targetReceiptMapper;
    private final OmsMemberTargetReceiptPayMapper targetPayMapper;

    public MemberBenefitReceiptDetailVO detail(String receiptKind, String receiptNo) {
        if (!"AMOUNT".equals(receiptKind) && !"TARGET".equals(receiptKind)) {
            throw new BaseException("权益凭证类型不合法");
        }
        if (receiptNo == null || receiptNo.trim().isEmpty()) {
            throw new BaseException("权益凭证号不能为空");
        }
        return "AMOUNT".equals(receiptKind) ? amount(receiptNo) : target(receiptNo);
    }

    private MemberBenefitReceiptDetailVO amount(String receiptNo) {
        OmsMemberAmountReceipt row = amountReceiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountReceipt>()
                .eq(OmsMemberAmountReceipt::getReceiptNo, receiptNo));
        if (row == null) throw new BaseException("金额权益凭证不存在");
        MemberBenefitReceiptDetailVO result = common("AMOUNT", row.getReceiptNo(), row.getReceiptType(), row.getMemberId(),
                row.getBrandId(), row.getAmountRightId(), row.getTotalAmount(), row.getRightDeductAmount(), row.getSupplementAmount(),
                row.getStatus(), null, row.getCreateTime());
        result.setPayments(amountPayMapper.selectList(new LambdaQueryWrapper<OmsMemberAmountReceiptPay>()
                        .eq(OmsMemberAmountReceiptPay::getReceiptNo, receiptNo).orderByAsc(OmsMemberAmountReceiptPay::getId))
                .stream().map(pay -> payment(pay.getPayMethodCode(), pay.getPayMethodName(), pay.getPayTag(), pay.getNetAmount())).collect(Collectors.toList()));
        return result;
    }

    private MemberBenefitReceiptDetailVO target(String receiptNo) {
        OmsMemberTargetReceipt row = targetReceiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberTargetReceipt>()
                .eq(OmsMemberTargetReceipt::getReceiptNo, receiptNo));
        if (row == null) throw new BaseException("升级计划凭证不存在");
        MemberBenefitReceiptDetailVO result = common("TARGET", row.getReceiptNo(), row.getReceiptType(), row.getMemberId(),
                null, row.getTargetPlanId(), row.getAmount(), BigDecimal.ZERO, row.getAmount(), row.getStatus(), row.getReason(), row.getCreateTime());
        result.setPayments(targetPayMapper.selectList(new LambdaQueryWrapper<OmsMemberTargetReceiptPay>()
                        .eq(OmsMemberTargetReceiptPay::getReceiptNo, receiptNo).orderByAsc(OmsMemberTargetReceiptPay::getId))
                .stream().map(pay -> payment(pay.getPayMethodCode(), pay.getPayMethodName(), pay.getPayTag(), pay.getPayAmount())).collect(Collectors.toList()));
        return result;
    }

    private MemberBenefitReceiptDetailVO common(String kind, String no, String type, Long memberId, String brandId, Long relatedId,
                                                  BigDecimal total, BigDecimal rightDeduct, BigDecimal supplement, String status,
                                                  String reason, java.time.LocalDateTime createTime) {
        MemberBenefitReceiptDetailVO result = new MemberBenefitReceiptDetailVO();
        result.setReceiptKind(kind); result.setReceiptNo(no); result.setReceiptType(type); result.setMemberId(memberId);
        result.setBrandId(brandId); result.setRightOrPlanId(relatedId); result.setTotalAmount(total); result.setRightDeductAmount(rightDeduct);
        result.setSupplementAmount(supplement); result.setStatus(status); result.setReason(reason); result.setCreateTime(createTime);
        return result;
    }

    private MemberBenefitReceiptDetailVO.Payment payment(String code, String name, String tag, BigDecimal amount) {
        MemberBenefitReceiptDetailVO.Payment result = new MemberBenefitReceiptDetailVO.Payment();
        result.setPayMethodCode(code); result.setPayMethodName(name); result.setPayTag(tag); result.setAmount(amount); return result;
    }
}
