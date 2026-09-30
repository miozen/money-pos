package com.money.feature.trade.application.memberpickup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.memberbenefit.MemberBenefitTradeHistoryVO;
import com.money.feature.trade.infrastructure.persistence.entity.*;
import com.money.feature.trade.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** TRADE-owned history read model; UMS entities and mappers never cross this boundary. */
@Service
@RequiredArgsConstructor
public class MemberBenefitTradeHistoryService {
    private final OmsMemberQuantityPickupMapper quantityPickupMapper;
    private final OmsMemberAmountPickupMapper amountPickupMapper;
    private final OmsMemberAmountReceiptMapper amountReceiptMapper;
    private final OmsMemberTargetReceiptMapper targetReceiptMapper;

    public MemberBenefitTradeHistoryVO listByMember(Long memberId) {
        MemberBenefitTradeHistoryVO result = new MemberBenefitTradeHistoryVO();
        if (memberId == null) { result.setRecords(java.util.Collections.emptyList()); return result; }
        List<MemberBenefitTradeHistoryVO.Record> records = new ArrayList<>();
        quantityPickupMapper.selectList(new LambdaQueryWrapper<OmsMemberQuantityPickup>()
                        .eq(OmsMemberQuantityPickup::getMemberId, memberId)).forEach(row -> records.add(record(
                "QUANTITY_PICKUP", row.getPickupNo(), row.getStatus(), BigDecimal.ZERO, row.getRequestNo(), null, row.getCreateTime())));
        amountPickupMapper.selectList(new LambdaQueryWrapper<OmsMemberAmountPickup>()
                        .eq(OmsMemberAmountPickup::getMemberId, memberId)).forEach(row -> records.add(record(
                "AMOUNT_PICKUP", row.getPickupNo(), row.getStatus(), BigDecimal.ZERO, row.getSupplementReceiptNo(), row.getRefundNo(), row.getCreateTime())));
        amountReceiptMapper.selectList(new LambdaQueryWrapper<OmsMemberAmountReceipt>()
                        .eq(OmsMemberAmountReceipt::getMemberId, memberId)).forEach(row -> records.add(record(
                row.getReceiptType(), row.getReceiptNo(), row.getStatus(), row.getSupplementAmount(), row.getSourcePickupNo(), null, row.getCreateTime())));
        targetReceiptMapper.selectList(new LambdaQueryWrapper<OmsMemberTargetReceipt>()
                        .eq(OmsMemberTargetReceipt::getMemberId, memberId)).forEach(row -> records.add(record(
                "TARGET_" + row.getReceiptType(), row.getReceiptNo(), row.getStatus(), row.getAmount(), String.valueOf(row.getTargetPlanId()), null, row.getCreateTime())));
        records.sort(Comparator.comparing(MemberBenefitTradeHistoryVO.Record::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(MemberBenefitTradeHistoryVO.Record::getReferenceNo));
        result.setRecords(records); return result;
    }

    private MemberBenefitTradeHistoryVO.Record record(String type, String referenceNo, String status, BigDecimal amount,
                                                        String sourceNo, String refundNo, java.time.LocalDateTime createTime) {
        MemberBenefitTradeHistoryVO.Record r = new MemberBenefitTradeHistoryVO.Record(); r.setRecordType(type); r.setReferenceNo(referenceNo);
        r.setStatus(status); r.setAmount(amount == null ? BigDecimal.ZERO : amount); r.setSourceNo(sourceNo); r.setRefundNo(refundNo); r.setCreateTime(createTime); return r;
    }
}
