package com.money.feature.ums.application.cloudsync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.BrandNameQuery;
import com.money.contract.goods.GoodsNameQuery;
import com.money.dto.cloud.MemberCloudSnapshot;
import com.money.feature.ums.infrastructure.persistence.entity.*;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberAmountRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberQuantityRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberTargetPlanMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.service.SysDictDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Builds the current UMS-owned projection; callers must never reconstruct assets from cloud data. */
@Service
@RequiredArgsConstructor
public class MemberCloudSnapshotAssembler {
    private final UmsMemberMapper memberMapper;
    private final UmsMemberBrandLevelMapper brandLevelMapper;
    private final PosMemberCouponMapper couponMapper;
    private final UmsMemberQuantityRightMapper quantityRightMapper;
    private final UmsMemberAmountRightMapper amountRightMapper;
    private final UmsMemberTargetPlanMapper targetPlanMapper;
    private final GoodsNameQuery goodsNameQuery;
    private final BrandNameQuery brandNameQuery;
    private final SysDictDetailService sysDictDetailService;

    @Transactional(readOnly = true)
    public MemberCloudSnapshot assemble(Long memberId) {
        if (memberId == null) return null;
        UmsMember member = memberMapper.selectById(memberId);
        if (member == null) return null;
        MemberCloudSnapshot snapshot = new MemberCloudSnapshot();
        snapshot.setMemberId(member.getId()); snapshot.setTenantId(member.getTenantId());
        snapshot.setStatus(Boolean.TRUE.equals(member.getDeleted()) ? "DELETED" : "ACTIVE");
        snapshot.setMergedToMemberId(member.getMergedToMemberId()); snapshot.setCode(member.getCode());
        snapshot.setName(member.getName()); snapshot.setPhone(member.getPhone()); snapshot.setType(member.getType());
        snapshot.setBalance(zero(member.getBalance())); snapshot.setCouponBalance(zero(member.getCoupon()));
        snapshot.setSourceUpdatedAt(member.getUpdateTime());
        BrandLevelProjection brandLevels = brandLevels(memberId);
        snapshot.setBrandLevels(brandLevels.codes);
        snapshot.setBrandLevelDescriptions(brandLevels.descriptions);
        snapshot.setUnusedCoupons(coupons(memberId));
        snapshot.setQuantityRights(quantityRights(memberId));
        snapshot.setAmountRights(amountRights(memberId));
        snapshot.setTargetPlans(targetPlans(memberId));
        return snapshot;
    }

    private BrandLevelProjection brandLevels(Long memberId) {
        List<UmsMemberBrandLevel> levels = brandLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberId));
        Map<String, String> codes = levels.stream().collect(Collectors.toMap(UmsMemberBrandLevel::getBrand, UmsMemberBrandLevel::getLevelCode, (a, b) -> b, LinkedHashMap::new));
        Map<String, String> names = brandNameQuery.findNamesByIds(codes.keySet());
        Map<String, String> levelNames = sysDictDetailService.getValueToCnDescMap("memberType");
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : codes.entrySet()) {
            String brandName = names.getOrDefault(entry.getKey(), "未知品牌");
            String levelCode = entry.getValue();
            String levelName = levelNames == null || levelCode == null ? levelCode
                    : levelNames.getOrDefault(levelCode.trim().toUpperCase(Locale.ROOT), levelCode);
            descriptions.put(brandName, levelName);
        }
        return new BrandLevelProjection(codes, descriptions);
    }
    private List<MemberCloudSnapshot.CouponWallet> coupons(Long memberId) {
        Map<Long, Long> counts = couponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getMemberId, memberId).eq(PosMemberCoupon::getStatus, "UNUSED"))
                .stream().collect(Collectors.groupingBy(PosMemberCoupon::getRuleId, LinkedHashMap::new, Collectors.counting()));
        List<MemberCloudSnapshot.CouponWallet> result = new ArrayList<>();
        counts.forEach((ruleId, count) -> { MemberCloudSnapshot.CouponWallet row = new MemberCloudSnapshot.CouponWallet(); row.setRuleId(ruleId); row.setCount(count); result.add(row); });
        return result;
    }
    private List<MemberCloudSnapshot.QuantityRight> quantityRights(Long memberId) {
        List<UmsMemberQuantityRight> rows = quantityRightMapper.selectList(new LambdaQueryWrapper<UmsMemberQuantityRight>().eq(UmsMemberQuantityRight::getMemberId, memberId));
        Map<Long, String> names = goodsNameQuery.findNamesByGoodsIds(rows.stream().map(UmsMemberQuantityRight::getGoodsId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream().map(row -> { MemberCloudSnapshot.QuantityRight r = new MemberCloudSnapshot.QuantityRight(); r.setRightId(row.getId()); r.setBrandId(row.getBrandId()); r.setGoodsId(row.getGoodsId()); r.setGoodsName(names.get(row.getGoodsId())); r.setRemainingQuantity(row.getRemainingQuantity()); r.setStatus(row.getStatus()); return r; }).collect(Collectors.toList());
    }
    private List<MemberCloudSnapshot.AmountRight> amountRights(Long memberId) {
        return amountRightMapper.selectList(new LambdaQueryWrapper<UmsMemberAmountRight>().eq(UmsMemberAmountRight::getMemberId, memberId)).stream()
                .map(row -> { MemberCloudSnapshot.AmountRight r = new MemberCloudSnapshot.AmountRight(); r.setRightId(row.getId()); r.setBrandId(row.getBrandId()); r.setTierCode(row.getTierCodeSnapshot()); r.setTierName(row.getTierNameSnapshot()); r.setPricingLevelCode(row.getPricingLevelCodeSnapshot()); r.setRemainingAmount(zero(row.getRemainingAmount())); r.setStatus(row.getStatus()); return r; }).collect(Collectors.toList());
    }
    private List<MemberCloudSnapshot.TargetPlan> targetPlans(Long memberId) {
        return targetPlanMapper.selectList(new LambdaQueryWrapper<UmsMemberTargetPlan>().eq(UmsMemberTargetPlan::getMemberId, memberId)).stream()
                .map(row -> { MemberCloudSnapshot.TargetPlan r = new MemberCloudSnapshot.TargetPlan(); r.setPlanId(row.getId()); r.setBrandId(row.getBrandId()); r.setCurrentLevelCode(row.getCurrentLevelCodeSnapshot()); r.setTargetTierCode(row.getTargetTierCodeSnapshot()); r.setTargetTierName(row.getTargetTierNameSnapshot()); r.setTargetPricingLevelCode(row.getTargetPricingLevelCodeSnapshot()); r.setTargetAmount(zero(row.getTargetAmount())); r.setProgressAmount(zero(row.getProgressAmount())); r.setRemainingAmount(zero(row.getTargetAmount()).subtract(zero(row.getProgressAmount())).max(BigDecimal.ZERO)); r.setStatus(row.getStatus()); return r; }).collect(Collectors.toList());
    }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static class BrandLevelProjection {
        private final Map<String, String> codes;
        private final Map<String, String> descriptions;
        private BrandLevelProjection(Map<String, String> codes, Map<String, String> descriptions) { this.codes = codes; this.descriptions = descriptions; }
    }
}
