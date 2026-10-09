package com.money.feature.ums.application.memberbenefit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.BrandNameQuery;
import com.money.contract.goods.GoodsNameQuery;
import com.money.dto.memberbenefit.MemberBenefitOverviewVO;
import com.money.dto.memberbenefit.MemberTargetProgressLogVO;
import com.money.dto.memberbenefit.MemberTargetPlanOptionVO;
import com.money.feature.ums.infrastructure.persistence.entity.*;
import com.money.feature.ums.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** UMS-owned read facade for member-benefit configuration, balances and TARGET audit history. */
@Service
@RequiredArgsConstructor
public class MemberBenefitReadService {
    private final UmsBrandBenefitTierMapper tierMapper;
    private final UmsMemberQuantityRightMapper quantityRightMapper;
    private final UmsMemberAmountRightMapper amountRightMapper;
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

    /** Only in-progress plans for the explicit, single-brand normal-checkout choice. */
    public List<MemberTargetPlanOptionVO> targetPlanOptions(Long memberId, String brandId) {
        if (memberId == null || !text(brandId)) return java.util.Collections.emptyList();
        String name = brandNameQuery.findNamesByIds(java.util.Collections.singleton(brandId)).get(brandId);
        return targetPlanMapper.selectList(new LambdaQueryWrapper<UmsMemberTargetPlan>()
                .eq(UmsMemberTargetPlan::getMemberId, memberId).eq(UmsMemberTargetPlan::getBrandId, brandId)
                .eq(UmsMemberTargetPlan::getStatus, "IN_PROGRESS").orderByDesc(UmsMemberTargetPlan::getId))
                .stream().map(row -> {
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
    private boolean text(String value) { return value != null && !value.trim().isEmpty(); }
}
