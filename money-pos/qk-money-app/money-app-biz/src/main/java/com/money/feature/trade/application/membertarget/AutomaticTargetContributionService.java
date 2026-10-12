package com.money.feature.trade.application.membertarget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberBrandBenefitLedgerCommand;
import com.money.contract.member.MemberBrandBenefitLedgerCommandHandler;
import com.money.contract.member.MemberTargetPlanQuery;
import com.money.contract.member.MemberTargetPlanSnapshot;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetSaleContribution;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetSaleContributionMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Adds ordinary-sale TARGET progress only from persisted order facts, never from POS input. */
@Service
@RequiredArgsConstructor
public class AutomaticTargetContributionService {
    private final MemberTargetPlanQuery targetPlanQuery;
    private final MemberBrandBenefitLedgerCommandHandler ledger;
    private final OmsMemberTargetSaleContributionMapper contributionMapper;

    public void contribute(OmsOrder order, List<OmsOrderDetail> details) {
        if (order.getMemberId() == null || details == null || details.isEmpty()) return;
        Set<String> brands = details.stream().map(OmsOrderDetail::getBrandId).filter(java.util.Objects::nonNull)
                .map(String::valueOf).collect(Collectors.toSet());
        if (brands.isEmpty()) return;
        List<MemberTargetPlanSnapshot> candidates = targetPlanQuery.findEligibleForAutomaticContribution(order.getMemberId(), brands);
        Map<String, List<MemberTargetPlanSnapshot>> plansByBrand = candidates.stream()
                .collect(Collectors.groupingBy(MemberTargetPlanSnapshot::getBrandId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<MemberTargetPlanSnapshot>> entry : plansByBrand.entrySet()) {
            if (entry.getValue().size() > 1) throw new BaseException("会员品牌“" + entry.getKey() + "”存在多个可计入TARGET计划，请先处理计划");
        }
        Map<String, BigDecimal> amounts = netAmountsByBrand(details);
        for (MemberTargetPlanSnapshot plan : candidates) {
            BigDecimal amount = amounts.getOrDefault(plan.getBrandId(), BigDecimal.ZERO);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            OmsMemberTargetSaleContribution existing = contributionMapper.selectOne(new LambdaQueryWrapper<OmsMemberTargetSaleContribution>()
                    .eq(OmsMemberTargetSaleContribution::getTargetPlanId, plan.getPlanId())
                    .eq(OmsMemberTargetSaleContribution::getOrderNo, order.getOrderNo()));
            if (existing != null) continue;
            OmsMemberTargetSaleContribution row = new OmsMemberTargetSaleContribution();
            row.setTargetPlanId(plan.getPlanId()); row.setOrderNo(order.getOrderNo()); row.setMemberId(order.getMemberId());
            row.setBrandId(plan.getBrandId()); row.setContributionAmount(amount); row.setStatus("COMPLETED"); contributionMapper.insert(row);
            MemberBrandBenefitLedgerCommand.TargetProgressChange change = new MemberBrandBenefitLedgerCommand.TargetProgressChange();
            change.setPlanId(plan.getPlanId()); change.setDelta(amount); change.setAction("SALE_CONTRIBUTION");
            change.setRequestNo(order.getOrderNo() + "-TARGET-" + plan.getPlanId()); change.setSourceType("TRADE_ORDER");
            change.setSourceNo(order.getOrderNo()); change.setReason("普通结算按品牌明细自动计入"); ledger.changeTargetProgress(change);
        }
    }

    /**
     * TARGET progress is based on each persisted detail's own settled amount.
     * Manual order discounts and full-reduction vouchers remain order-level settlement facts:
     * they are deliberately not attributed to, or deducted from, any TARGET plan.
     */
    private Map<String, BigDecimal> netAmountsByBrand(List<OmsOrderDetail> details) {
        return details.stream()
                .map(detail -> new LineAmount(String.valueOf(detail.getBrandId()),
                        nonNegative(money(detail.getGoodsPrice()).multiply(BigDecimal.valueOf(detail.getQuantity()))).subtract(money(detail.getCoupon()))))
                .collect(Collectors.groupingBy(LineAmount::getBrandId, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, LineAmount::getAmount, BigDecimal::add)));
    }

    private static BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal nonNegative(BigDecimal value) { return value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); }
    private static final class LineAmount {
        private final String brandId;
        private final BigDecimal amount;
        private LineAmount(String brandId, BigDecimal amount) { this.brandId = brandId; this.amount = amount; }
        private String getBrandId() { return brandId; }
        private BigDecimal getAmount() { return amount; }
    }
}
