package com.money.feature.ums.application.memberbenefit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.BrandNameQuery;
import com.money.contract.goods.GoodsNameQuery;
import com.money.dto.memberbenefit.MemberBenefitOverviewVO;
import com.money.dto.memberbenefit.MemberTargetProgressLogVO;
import com.money.dto.memberbenefit.MemberTargetPlanOptionVO;
import com.money.dto.memberbenefit.MemberBenefitOperationHistoryVO;
import com.money.feature.ums.infrastructure.persistence.entity.*;
import com.money.feature.ums.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** UMS-owned read facade for member-benefit configuration, balances and TARGET audit history. */
@Service
@RequiredArgsConstructor
public class MemberBenefitReadService {
    private final UmsBrandBenefitTierMapper tierMapper;
    private final UmsMemberQuantityRightMapper quantityRightMapper;
    private final UmsMemberQuantityRightLogMapper quantityLogMapper;
    private final UmsMemberAmountRightMapper amountRightMapper;
    private final UmsMemberAmountRightLogMapper amountLogMapper;
    private final UmsMemberTargetPlanMapper targetPlanMapper;
    private final UmsMemberTargetProgressLogMapper targetLogMapper;
    private final BrandNameQuery brandNameQuery;
    private final GoodsNameQuery goodsNameQuery;

    public MemberBenefitOverviewVO overview(Long memberId, String brandId) {
        MemberBenefitOverviewVO result = new MemberBenefitOverviewVO();
        LambdaQueryWrapper<UmsBrandBenefitTier> tiers = new LambdaQueryWrapper<UmsBrandBenefitTier>()
                .orderByAsc(UmsBrandBenefitTier::getBrandId).orderByAsc(UmsBrandBenefitTier::getSortNo);
        if (text(brandId)) tiers.eq(UmsBrandBenefitTier::getBrandId, brandId);
        List<UmsBrandBenefitTier> tierRows = tierMapper.selectList(tiers);
        if (memberId == null) {
            result.setTiers(tierRows.stream().map(this::tier).collect(Collectors.toList()));
            result.setQuantityRights(java.util.Collections.emptyList()); result.setAmountRights(java.util.Collections.emptyList());
            result.setTargetPlans(java.util.Collections.emptyList());
            applyDisplayNames(result);
            return result;
        }
        LambdaQueryWrapper<UmsMemberQuantityRight> quantities = new LambdaQueryWrapper<UmsMemberQuantityRight>()
                .eq(UmsMemberQuantityRight::getMemberId, memberId).orderByDesc(UmsMemberQuantityRight::getId);
        if (text(brandId)) quantities.eq(UmsMemberQuantityRight::getBrandId, brandId);
        result.setTiers(tierRows.stream().map(this::tier).collect(Collectors.toList()));
        result.setQuantityRights(quantityRightMapper.selectList(quantities).stream().map(this::quantity).collect(Collectors.toList()));
        LambdaQueryWrapper<UmsMemberAmountRight> amounts = new LambdaQueryWrapper<UmsMemberAmountRight>()
                .eq(UmsMemberAmountRight::getMemberId, memberId).orderByDesc(UmsMemberAmountRight::getId);
        if (text(brandId)) amounts.eq(UmsMemberAmountRight::getBrandId, brandId);
        result.setAmountRights(amountRightMapper.selectList(amounts).stream().map(this::amount).collect(Collectors.toList()));
        LambdaQueryWrapper<UmsMemberTargetPlan> targets = new LambdaQueryWrapper<UmsMemberTargetPlan>()
                .eq(UmsMemberTargetPlan::getMemberId, memberId).orderByDesc(UmsMemberTargetPlan::getId);
        if (text(brandId)) targets.eq(UmsMemberTargetPlan::getBrandId, brandId);
        result.setTargetPlans(targetPlanMapper.selectList(targets).stream().map(this::target).collect(Collectors.toList()));
        applyDisplayNames(result);
        return result;
    }

    public List<MemberTargetProgressLogVO> targetLogs(Long planId) {
        if (planId == null) return java.util.Collections.emptyList();
        return targetLogMapper.selectList(new LambdaQueryWrapper<UmsMemberTargetProgressLog>()
                .eq(UmsMemberTargetProgressLog::getPlanId, planId).orderByDesc(UmsMemberTargetProgressLog::getId))
                .stream().map(this::log).collect(Collectors.toList());
    }

    /** Bounded workspace history. It deliberately reads only ledger rows owned by the selected member. */
    public MemberBenefitOperationHistoryVO operationHistory(Long memberId, String benefitType, Integer requestedLimit) {
        MemberBenefitOperationHistoryVO result = new MemberBenefitOperationHistoryVO();
        result.setRecords(new ArrayList<>());
        if (memberId == null || !text(benefitType)) return result;
        int limit = Math.max(1, Math.min(requestedLimit == null ? 10 : requestedLimit, 20));
        if ("QUANTITY".equals(benefitType)) {
            List<UmsMemberQuantityRight> rights = quantityRightMapper.selectList(new LambdaQueryWrapper<UmsMemberQuantityRight>()
                    .eq(UmsMemberQuantityRight::getMemberId, memberId));
            if (rights.isEmpty()) return result;
            Map<Long, UmsMemberQuantityRight> byId = rights.stream().collect(Collectors.toMap(UmsMemberQuantityRight::getId, x -> x));
            Map<Long, String> goods = goodsNameQuery.findNamesByGoodsIds(rights.stream().map(UmsMemberQuantityRight::getGoodsId).collect(Collectors.toSet()));
            quantityLogMapper.selectList(new LambdaQueryWrapper<UmsMemberQuantityRightLog>().in(UmsMemberQuantityRightLog::getRightId, byId.keySet())
                    .orderByDesc(UmsMemberQuantityRightLog::getCreateTime).orderByDesc(UmsMemberQuantityRightLog::getId).last("LIMIT " + limit))
                    .forEach(log -> result.getRecords().add(quantityHistory(log, byId.get(log.getRightId()), goods.get(byId.get(log.getRightId()).getGoodsId()))));
            return result;
        }
        if ("AMOUNT".equals(benefitType)) {
            List<UmsMemberAmountRight> rights = amountRightMapper.selectList(new LambdaQueryWrapper<UmsMemberAmountRight>()
                    .eq(UmsMemberAmountRight::getMemberId, memberId));
            if (rights.isEmpty()) return result;
            Map<Long, UmsMemberAmountRight> byId = rights.stream().collect(Collectors.toMap(UmsMemberAmountRight::getId, x -> x));
            amountLogMapper.selectList(new LambdaQueryWrapper<UmsMemberAmountRightLog>().in(UmsMemberAmountRightLog::getRightId, byId.keySet())
                    .orderByDesc(UmsMemberAmountRightLog::getCreateTime).orderByDesc(UmsMemberAmountRightLog::getId).last("LIMIT " + limit))
                    .forEach(log -> result.getRecords().add(amountHistory(log, byId.get(log.getRightId()))));
            return result;
        }
        if ("TARGET".equals(benefitType)) {
            List<UmsMemberTargetPlan> plans = targetPlanMapper.selectList(new LambdaQueryWrapper<UmsMemberTargetPlan>()
                    .eq(UmsMemberTargetPlan::getMemberId, memberId));
            if (plans.isEmpty()) return result;
            Map<Long, UmsMemberTargetPlan> byId = plans.stream().collect(Collectors.toMap(UmsMemberTargetPlan::getId, x -> x));
            targetLogMapper.selectList(new LambdaQueryWrapper<UmsMemberTargetProgressLog>().in(UmsMemberTargetProgressLog::getPlanId, byId.keySet())
                    .orderByDesc(UmsMemberTargetProgressLog::getCreateTime).orderByDesc(UmsMemberTargetProgressLog::getId).last("LIMIT " + limit))
                    .forEach(log -> result.getRecords().add(targetHistory(log, byId.get(log.getPlanId()))));
        }
        return result;
    }

    /** Only automatically eligible plans for a normal checkout brand. */
    public List<MemberTargetPlanOptionVO> targetPlanOptions(Long memberId, String brandId) {
        if (memberId == null || !text(brandId)) return java.util.Collections.emptyList();
        String name = brandNameQuery.findNamesByIds(java.util.Collections.singleton(brandId)).get(brandId);
        return targetPlanMapper.selectList(new LambdaQueryWrapper<UmsMemberTargetPlan>()
                .eq(UmsMemberTargetPlan::getMemberId, memberId).eq(UmsMemberTargetPlan::getBrandId, brandId)
                .eq(UmsMemberTargetPlan::getStatus, "IN_PROGRESS").orderByDesc(UmsMemberTargetPlan::getId))
                .stream().filter(row -> row.getProgressAmount().compareTo(row.getTargetAmount()) < 0)
                .map(row -> {
                    MemberTargetPlanOptionVO option = new MemberTargetPlanOptionVO();
                    option.setPlanId(row.getId()); option.setBrandId(row.getBrandId()); option.setBrandName(name);
                    option.setTargetTierName(row.getTargetTierNameSnapshot()); option.setTargetAmount(row.getTargetAmount());
                    option.setProgressAmount(row.getProgressAmount());
                    option.setRemainingAmount(row.getTargetAmount().subtract(row.getProgressAmount()).max(java.math.BigDecimal.ZERO));
                    return option;
                }).collect(Collectors.toList());
    }

    private MemberBenefitOverviewVO.Tier tier(UmsBrandBenefitTier row) { MemberBenefitOverviewVO.Tier r = new MemberBenefitOverviewVO.Tier(); r.setBrandId(row.getBrandId()); r.setTierCode(row.getTierCode()); r.setTierName(row.getTierName()); r.setConfiguredAmount(row.getConfiguredAmount()); r.setPricingLevelCode(row.getPricingLevelCode()); r.setRankValue(row.getRankValue()); r.setEnabled(row.getEnabled()); r.setSortNo(row.getSortNo()); return r; }
    private MemberBenefitOverviewVO.QuantityRight quantity(UmsMemberQuantityRight row) { MemberBenefitOverviewVO.QuantityRight r = new MemberBenefitOverviewVO.QuantityRight(); r.setRightId(row.getId()); r.setBrandId(row.getBrandId()); r.setGoodsId(row.getGoodsId()); r.setSourceOrderNo(row.getSourceOrderNo()); r.setGrantedQuantity(row.getGrantedQuantity()); r.setPickedQuantity(row.getPickedQuantity()); r.setRemainingQuantity(row.getRemainingQuantity()); r.setStatus(row.getStatus()); return r; }
    private MemberBenefitOverviewVO.AmountRight amount(UmsMemberAmountRight row) { MemberBenefitOverviewVO.AmountRight r = new MemberBenefitOverviewVO.AmountRight(); r.setRightId(row.getId()); r.setBrandId(row.getBrandId()); r.setTierCode(row.getTierCodeSnapshot()); r.setTierName(row.getTierNameSnapshot()); r.setPricingLevelCode(row.getPricingLevelCodeSnapshot()); r.setGrantedAmount(row.getGrantedAmount()); r.setRemainingAmount(row.getRemainingAmount()); r.setSourceReceiptNo(row.getSourceReceiptNo()); r.setStatus(row.getStatus()); return r; }
    private MemberBenefitOverviewVO.TargetPlan target(UmsMemberTargetPlan row) { MemberBenefitOverviewVO.TargetPlan r = new MemberBenefitOverviewVO.TargetPlan(); r.setPlanId(row.getId()); r.setBrandId(row.getBrandId()); r.setCurrentLevelCode(row.getCurrentLevelCodeSnapshot()); r.setTargetTierCode(row.getTargetTierCodeSnapshot()); r.setTargetTierName(row.getTargetTierNameSnapshot()); r.setTargetAmount(row.getTargetAmount()); r.setProgressAmount(row.getProgressAmount()); r.setStatus(row.getStatus()); r.setConfirmedTime(row.getConfirmedTime()); r.setRemark(row.getRemark()); return r; }
    private void applyDisplayNames(MemberBenefitOverviewVO result) {
        Set<String> brandIds = new java.util.LinkedHashSet<>();
        result.getTiers().forEach(row -> brandIds.add(row.getBrandId()));
        result.getQuantityRights().forEach(row -> brandIds.add(row.getBrandId()));
        result.getAmountRights().forEach(row -> brandIds.add(row.getBrandId()));
        result.getTargetPlans().forEach(row -> brandIds.add(row.getBrandId()));
        Map<String, String> brands = brandNameQuery.findNamesByIds(brandIds);
        result.getTiers().forEach(row -> { row.setBrandName(brands.get(row.getBrandId())); row.setPricingLevelName(row.getTierName()); });
        result.getQuantityRights().forEach(row -> row.setBrandName(brands.get(row.getBrandId())));
        result.getAmountRights().forEach(row -> { row.setBrandName(brands.get(row.getBrandId())); row.setPricingLevelName(row.getTierName()); });
        result.getTargetPlans().forEach(row -> row.setBrandName(brands.get(row.getBrandId())));
        Set<Long> goodsIds = result.getQuantityRights().stream().map(MemberBenefitOverviewVO.QuantityRight::getGoodsId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> goods = goodsNameQuery.findNamesByGoodsIds(goodsIds);
        result.getQuantityRights().forEach(row -> row.setGoodsName(goods.get(row.getGoodsId())));
    }
    private MemberTargetProgressLogVO log(UmsMemberTargetProgressLog row) { MemberTargetProgressLogVO r = new MemberTargetProgressLogVO(); r.setId(row.getId()); r.setAction(row.getAction()); r.setAmountDelta(row.getAmountDelta()); r.setBeforeAmount(row.getBeforeAmount()); r.setAfterAmount(row.getAfterAmount()); r.setRequestNo(row.getRequestNo()); r.setSourceType(row.getSourceType()); r.setSourceNo(row.getSourceNo()); r.setOperatorName(row.getOperatorName()); r.setReason(row.getReason()); r.setCreateTime(row.getCreateTime()); return r; }
    private MemberBenefitOperationHistoryVO.Record quantityHistory(UmsMemberQuantityRightLog log, UmsMemberQuantityRight right, String goodsName) { return history("QUANTITY", right.getId(), goodsName == null ? "寄存商品" : goodsName, log.getAction(), quantityAction(log.getAction()), BigDecimal.valueOf(log.getQuantityDelta()), BigDecimal.valueOf(log.getAfterQuantity()), "件", log.getSourceNo(), quantityTarget(log), log.getOperatorName(), right.getStatus(), log.getReason(), log.getCreateTime()); }
    private MemberBenefitOperationHistoryVO.Record amountHistory(UmsMemberAmountRightLog log, UmsMemberAmountRight right) { return history("AMOUNT", right.getId(), right.getTierNameSnapshot() == null ? "金额权益包" : right.getTierNameSnapshot(), log.getAction(), amountAction(log.getAction()), log.getAmountDelta(), log.getAfterAmount(), "元", log.getSourceNo(), amountTarget(log), log.getOperatorName(), right.getStatus(), log.getReason(), log.getCreateTime()); }
    private MemberBenefitOperationHistoryVO.Record targetHistory(UmsMemberTargetProgressLog log, UmsMemberTargetPlan plan) { return history("TARGET", plan.getId(), plan.getTargetTierNameSnapshot() == null ? "升级计划" : plan.getTargetTierNameSnapshot(), log.getAction(), targetAction(log.getAction()), log.getAmountDelta(), log.getAfterAmount(), "进度", log.getSourceNo(), targetTarget(log), log.getOperatorName(), plan.getStatus(), log.getReason(), log.getCreateTime()); }
    private MemberBenefitOperationHistoryVO.Record history(String type, Long id, String subject, String action, String actionName, BigDecimal delta, BigDecimal after, String unit, String reference, String target, String operator, String status, String reason, java.time.LocalDateTime time) { MemberBenefitOperationHistoryVO.Record r = new MemberBenefitOperationHistoryVO.Record(); r.setBenefitType(type); r.setSubjectId(id); r.setSubjectName(subject); r.setAction(action); r.setActionName(actionName); r.setDelta(delta); r.setAfterValue(after); r.setUnit(unit); r.setReferenceNo(reference); r.setDetailTarget(target); r.setOperatorName(operator); r.setStatus(status); r.setReason(reason); r.setCreateTime(time); return r; }
    private String quantityAction(String action) { if ("GRANT".equals(action)) return "办理寄存"; if ("PICKUP_DEDUCT".equals(action)) return "会员提货"; if ("REFUND_CANCEL".equals(action)) return "退款扣回未提货权益"; return action; }
    private String amountAction(String action) { if ("GRANT".equals(action)) return "办理金额权益包"; if ("PICKUP_DEDUCT".equals(action)) return "金额权益提货"; if ("PICKUP_REFUND_RESTORE".equals(action)) return "提货退款恢复权益"; return action; }
    private String targetAction(String action) { if ("INITIAL_PROGRESS".equals(action)) return "建立升级计划"; if ("SALE_CONTRIBUTION".equals(action)) return "订单计入升级进度"; if ("SUPPLEMENT".equals(action)) return "升级补差"; if ("WAIVER".equals(action)) return "升级豁免"; if ("MANUAL_CONFIRM".equals(action)) return "人工确认升级"; if ("CANCELLED".equals(action)) return "取消升级计划"; if ("REFUND_REVIEW_REQUIRED".equals(action)) return "退款待人工复核"; return action; }
    private String quantityTarget(UmsMemberQuantityRightLog log) { if ("GRANT".equals(log.getAction()) || "REFUND_CANCEL".equals(log.getAction())) return "ORDER"; return "PICKUP_DEDUCT".equals(log.getAction()) ? "QUANTITY_PICKUP" : "NONE"; }
    private String amountTarget(UmsMemberAmountRightLog log) { if ("GRANT".equals(log.getAction())) return "AMOUNT_RECEIPT"; return "PICKUP_DEDUCT".equals(log.getAction()) ? "AMOUNT_PICKUP" : "NONE"; }
    private String targetTarget(UmsMemberTargetProgressLog log) { if ("SUPPLEMENT".equals(log.getAction()) || "WAIVER".equals(log.getAction())) return "TARGET_RECEIPT"; return ("SALE_CONTRIBUTION".equals(log.getAction()) || "REFUND_REVIEW_REQUIRED".equals(log.getAction())) && ("TRADE_ORDER".equals(log.getSourceType()) || "TARGET_SALE".equals(log.getSourceType()) || "TARGET_SALE_REFUND".equals(log.getSourceType())) ? "ORDER" : "NONE"; }
    private boolean text(String value) { return value != null && !value.trim().isEmpty(); }
}
