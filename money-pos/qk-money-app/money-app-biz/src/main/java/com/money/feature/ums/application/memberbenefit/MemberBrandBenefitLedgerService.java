package com.money.feature.ums.application.memberbenefit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberBrandBenefitLedgerCommand;
import com.money.contract.member.MemberBrandBenefitLedgerCommandHandler;
import com.money.contract.member.MemberQuantityPickupCommand;
import com.money.contract.member.MemberQuantityPickupCommandHandler;
import com.money.contract.member.MemberQuantityRightQuery;
import com.money.contract.member.MemberQuantityRightSnapshot;
import com.money.contract.member.MemberQuantityRefundCommand;
import com.money.contract.member.MemberQuantityRefundCommandHandler;
import com.money.contract.member.MemberQuantityRefundResult;
import com.money.feature.ums.infrastructure.persistence.entity.*;
import com.money.feature.ums.infrastructure.persistence.mapper.*;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UMS ledger writer for ME-1.1. Later features may call this boundary, but may not update a right balance directly.
 */
@Service
@RequiredArgsConstructor
public class MemberBrandBenefitLedgerService implements MemberBrandBenefitLedgerCommandHandler,
        MemberQuantityPickupCommandHandler, MemberQuantityRightQuery, MemberQuantityRefundCommandHandler {
    private final UmsBrandBenefitTierMapper tierMapper;
    private final UmsMemberQuantityRightMapper quantityRightMapper;
    private final UmsMemberQuantityRightLogMapper quantityLogMapper;
    private final UmsMemberAmountRightMapper amountRightMapper;
    private final UmsMemberAmountRightLogMapper amountLogMapper;
    private final UmsMemberTargetPlanMapper targetPlanMapper;
    private final UmsMemberTargetProgressLogMapper targetLogMapper;
    private final UmsMemberBrandLevelMapper brandLevelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grantQuantity(MemberBrandBenefitLedgerCommand.QuantityGrant c) {
        require(c != null && c.getMemberId() != null && c.getGoodsId() != null && positive(c.getQuantity())
                && text(c.getBrandId()) && text(c.getSourceOrderNo()) && text(c.getRequestNo()), "数量权益发放命令不完整");
        UmsMemberQuantityRight existing = quantityRightMapper.selectOne(new LambdaQueryWrapper<UmsMemberQuantityRight>()
                .eq(UmsMemberQuantityRight::getCreateRequestNo, c.getRequestNo()));
        if (existing != null) return existing.getId();
        UmsMemberQuantityRight right = new UmsMemberQuantityRight();
        right.setMemberId(c.getMemberId()); right.setBrandId(c.getBrandId()); right.setGoodsId(c.getGoodsId());
        right.setSourceOrderNo(c.getSourceOrderNo()); right.setSourceOrderDetailId(c.getSourceOrderDetailId());
        right.setCreateRequestNo(c.getRequestNo()); right.setGrantedQuantity(c.getQuantity());
        right.setPickedQuantity(0); right.setRemainingQuantity(c.getQuantity()); right.setStatus("ACTIVE");
        quantityRightMapper.insert(right);
        quantityLogMapper.insert(quantityLog(right.getId(), "GRANT", c.getQuantity(), 0, c.getQuantity(), c.getRequestNo(),
                "TRADE_ORDER", c.getSourceOrderNo(), c.getOperatorName(), c.getReason()));
        return right.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeQuantity(MemberBrandBenefitLedgerCommand.QuantityChange c) {
        require(c != null && c.getRightId() != null && c.getDelta() != null && text(c.getAction()) && text(c.getRequestNo()), "数量权益变动命令不完整");
        if (quantityLogMapper.exists(new LambdaQueryWrapper<UmsMemberQuantityRightLog>()
                .eq(UmsMemberQuantityRightLog::getRightId, c.getRightId()).eq(UmsMemberQuantityRightLog::getRequestNo, c.getRequestNo()))) return;
        UmsMemberQuantityRight right = quantityRightMapper.selectByIdForUpdate(c.getRightId());
        require(right != null && "ACTIVE".equals(right.getStatus()), "数量权益不存在或不可用");
        int before = right.getRemainingQuantity(); int after = before + c.getDelta();
        require(after >= 0, "数量权益不足");
        int pickedDelta = c.getPickedDelta() == null ? 0 : c.getPickedDelta();
        require(right.getPickedQuantity() + pickedDelta >= 0, "已提数量不能为负");
        require(quantityRightMapper.changeBalance(right.getId(), c.getDelta(), pickedDelta) == 1, "数量权益并发更新失败");
        quantityLogMapper.insert(quantityLog(right.getId(), c.getAction(), c.getDelta(), before, after, c.getRequestNo(),
                c.getSourceType(), c.getSourceNo(), c.getOperatorName(), c.getReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grantAmount(MemberBrandBenefitLedgerCommand.AmountGrant c) {
        require(c != null && c.getMemberId() != null && text(c.getBrandId()) && text(c.getTierCode()) && positive(c.getAmount())
                && text(c.getSourceReceiptNo()) && text(c.getRequestNo()), "金额权益发放命令不完整");
        UmsMemberAmountRight existing = amountRightMapper.selectOne(new LambdaQueryWrapper<UmsMemberAmountRight>()
                .eq(UmsMemberAmountRight::getCreateRequestNo, c.getRequestNo()));
        if (existing != null) return existing.getId();
        UmsBrandBenefitTier tier = enabledTier(c.getBrandId(), c.getTierCode());
        UmsMemberAmountRight right = new UmsMemberAmountRight();
        right.setMemberId(c.getMemberId()); right.setBrandId(c.getBrandId());
        right.setTierCodeSnapshot(tier.getTierCode()); right.setTierNameSnapshot(tier.getTierName());
        right.setPricingLevelCodeSnapshot(tier.getPricingLevelCode()); right.setGrantedAmount(c.getAmount());
        right.setRemainingAmount(c.getAmount()); right.setSourceReceiptNo(c.getSourceReceiptNo());
        right.setCreateRequestNo(c.getRequestNo()); right.setStatus("ACTIVE"); amountRightMapper.insert(right);
        amountLogMapper.insert(amountLog(right.getId(), "GRANT", c.getAmount(), BigDecimal.ZERO, c.getAmount(), c.getRequestNo(),
                "BUSINESS_RECEIPT", c.getSourceReceiptNo(), c.getOperatorName(), c.getReason()));
        return right.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeAmount(MemberBrandBenefitLedgerCommand.AmountChange c) {
        require(c != null && c.getRightId() != null && c.getDelta() != null && text(c.getAction()) && text(c.getRequestNo()), "金额权益变动命令不完整");
        if (amountLogMapper.exists(new LambdaQueryWrapper<UmsMemberAmountRightLog>()
                .eq(UmsMemberAmountRightLog::getRightId, c.getRightId()).eq(UmsMemberAmountRightLog::getRequestNo, c.getRequestNo()))) return;
        UmsMemberAmountRight right = amountRightMapper.selectByIdForUpdate(c.getRightId());
        require(right != null && "ACTIVE".equals(right.getStatus()), "金额权益不存在或不可用");
        BigDecimal before = right.getRemainingAmount(); BigDecimal after = before.add(c.getDelta());
        require(after.compareTo(BigDecimal.ZERO) >= 0, "金额权益不足");
        require(amountRightMapper.changeBalance(right.getId(), c.getDelta()) == 1, "金额权益并发更新失败");
        amountLogMapper.insert(amountLog(right.getId(), c.getAction(), c.getDelta(), before, after, c.getRequestNo(),
                c.getSourceType(), c.getSourceNo(), c.getOperatorName(), c.getReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTargetPlan(MemberBrandBenefitLedgerCommand.TargetPlanCreate c) {
        require(c != null && c.getMemberId() != null && text(c.getBrandId()) && text(c.getTargetTierCode()) && text(c.getRequestNo()), "TARGET计划创建命令不完整");
        UmsMemberTargetPlan existing = targetPlanMapper.selectOne(new LambdaQueryWrapper<UmsMemberTargetPlan>()
                .eq(UmsMemberTargetPlan::getCreateRequestNo, c.getRequestNo()));
        if (existing != null) return existing.getId();
        BigDecimal initial = c.getInitialProgress() == null ? BigDecimal.ZERO : c.getInitialProgress();
        require(initial.compareTo(BigDecimal.ZERO) >= 0, "TARGET初始进度不能为负");
        UmsBrandBenefitTier tier = enabledTier(c.getBrandId(), c.getTargetTierCode());
        require(initial.compareTo(tier.getConfiguredAmount()) <= 0, "TARGET初始进度不能超过目标金额");
        UmsMemberTargetPlan plan = new UmsMemberTargetPlan();
        plan.setMemberId(c.getMemberId()); plan.setBrandId(c.getBrandId()); plan.setCurrentLevelCodeSnapshot(c.getCurrentLevelCode());
        plan.setTargetTierCodeSnapshot(tier.getTierCode()); plan.setTargetTierNameSnapshot(tier.getTierName());
        plan.setTargetPricingLevelCodeSnapshot(tier.getPricingLevelCode()); plan.setTargetRankSnapshot(tier.getRankValue());
        plan.setTargetAmount(tier.getConfiguredAmount()); plan.setProgressAmount(initial); plan.setStatus("IN_PROGRESS");
        plan.setRemark(blank(c.getReason())); plan.setCreateRequestNo(c.getRequestNo()); targetPlanMapper.insert(plan);
        targetLogMapper.insert(targetLog(plan.getId(), "INITIAL_PROGRESS", initial, BigDecimal.ZERO, initial, c.getRequestNo(),
                c.getSourceType(), c.getSourceNo(), c.getOperatorName(), c.getReason()));
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeTargetProgress(MemberBrandBenefitLedgerCommand.TargetProgressChange c) {
        require(c != null && c.getPlanId() != null && c.getDelta() != null && text(c.getAction()) && text(c.getRequestNo()), "TARGET进度变动命令不完整");
        if (targetLogMapper.exists(new LambdaQueryWrapper<UmsMemberTargetProgressLog>()
                .eq(UmsMemberTargetProgressLog::getPlanId, c.getPlanId()).eq(UmsMemberTargetProgressLog::getRequestNo, c.getRequestNo()))) return;
        UmsMemberTargetPlan plan = targetPlanMapper.selectByIdForUpdate(c.getPlanId());
        require(plan != null && "IN_PROGRESS".equals(plan.getStatus()), "TARGET计划不存在或不可变动");
        BigDecimal before = plan.getProgressAmount(); BigDecimal after = before.add(c.getDelta());
        require(after.compareTo(BigDecimal.ZERO) >= 0, "TARGET进度不能为负");
        require(targetPlanMapper.changeProgress(plan.getId(), c.getDelta()) == 1, "TARGET进度并发更新失败");
        targetLogMapper.insert(targetLog(plan.getId(), c.getAction(), c.getDelta(), before, after, c.getRequestNo(),
                c.getSourceType(), c.getSourceNo(), c.getOperatorName(), c.getReason()));
    }

    /** Only this method may promote the current member-brand level from a configured benefit tier. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateTierIfHigher(Long memberId, String brandId, String tierCode) {
        require(memberId != null && text(brandId) && text(tierCode), "品牌等级激活命令不完整");
        UmsBrandBenefitTier candidate = enabledTier(brandId, tierCode);
        UmsMemberBrandLevel current = brandLevelMapper.selectOne(new LambdaQueryWrapper<UmsMemberBrandLevel>()
                .eq(UmsMemberBrandLevel::getMemberId, memberId).eq(UmsMemberBrandLevel::getBrand, brandId));
        if (current == null) {
            UmsMemberBrandLevel created = new UmsMemberBrandLevel();
            created.setMemberId(memberId); created.setBrand(brandId); created.setLevelCode(candidate.getPricingLevelCode());
            brandLevelMapper.insert(created); return;
        }
        UmsBrandBenefitTier currentTier = tierMapper.selectOne(new LambdaQueryWrapper<UmsBrandBenefitTier>()
                .eq(UmsBrandBenefitTier::getBrandId, brandId)
                .eq(UmsBrandBenefitTier::getPricingLevelCode, current.getLevelCode()));
        require(currentTier != null, "当前品牌等级未配置权益档位，不能自动变更");
        if (candidate.getRankValue() > currentTier.getRankValue()) {
            UmsMemberBrandLevel update = new UmsMemberBrandLevel();
            update.setId(current.getId()); update.setLevelCode(candidate.getPricingLevelCode());
            brandLevelMapper.updateById(update);
        }
    }

    @Override
    public List<MemberQuantityRightSnapshot> findAvailableForPickup(Long memberId, List<Long> rightIds) {
        if (memberId == null || rightIds == null || rightIds.isEmpty()) return java.util.Collections.emptyList();
        return quantityRightMapper.selectList(new LambdaQueryWrapper<UmsMemberQuantityRight>()
                .eq(UmsMemberQuantityRight::getMemberId, memberId).eq(UmsMemberQuantityRight::getStatus, "ACTIVE")
                .in(UmsMemberQuantityRight::getId, rightIds)).stream().map(right -> {
                    MemberQuantityRightSnapshot snapshot = new MemberQuantityRightSnapshot();
                    snapshot.setRightId(right.getId()); snapshot.setMemberId(right.getMemberId()); snapshot.setBrandId(right.getBrandId());
                    snapshot.setGoodsId(right.getGoodsId()); snapshot.setSourceOrderNo(right.getSourceOrderNo());
                    snapshot.setSourceOrderDetailId(right.getSourceOrderDetailId()); snapshot.setRemainingQuantity(right.getRemainingQuantity());
                    return snapshot;
                }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(MemberQuantityPickupCommand command) {
        require(command != null && command.getMemberId() != null && text(command.getPickupNo())
                && text(command.getRequestNo()) && command.getLines() != null && !command.getLines().isEmpty(), "提货权益命令不完整");
        for (MemberQuantityPickupCommand.Line line : command.getLines()) {
            require(line.getRightId() != null && positive(line.getQuantity()), "提货数量不合法");
            UmsMemberQuantityRight right = quantityRightMapper.selectByIdForUpdate(line.getRightId());
            require(right != null && command.getMemberId().equals(right.getMemberId()), "数量权益不属于当前会员");
            MemberBrandBenefitLedgerCommand.QuantityChange change = new MemberBrandBenefitLedgerCommand.QuantityChange();
            change.setRightId(right.getId()); change.setDelta(-line.getQuantity()); change.setPickedDelta(line.getQuantity());
            change.setAction("PICKUP_DEDUCT"); change.setRequestNo(command.getRequestNo() + "-" + right.getId());
            change.setSourceType("MEMBER_PICKUP"); change.setSourceNo(command.getPickupNo()); change.setOperatorName(command.getOperatorName());
            changeQuantity(change);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MemberQuantityRefundResult> handle(MemberQuantityRefundCommand command) {
        require(command != null && text(command.getRequestNo()) && text(command.getSourceOrderNo())
                && command.getLines() != null && !command.getLines().isEmpty(), "数量权益退款命令不完整");
        List<MemberQuantityRefundResult> results = new java.util.ArrayList<>();
        for (MemberQuantityRefundCommand.Line line : command.getLines()) {
            require(line != null && line.getSourceOrderDetailId() != null && positive(line.getQuantity()), "数量权益退款明细不完整");
            UmsMemberQuantityRight right = quantityRightMapper.selectBySourceOrderDetailIdForUpdate(line.getSourceOrderDetailId());
            if (right == null) {
                continue;
            }
            require(command.getSourceOrderNo().equals(right.getSourceOrderNo()), "数量权益来源订单不匹配");
            int remaining = right.getRemainingQuantity() == null ? 0 : right.getRemainingQuantity();
            int cancelled = Math.min(line.getQuantity(), remaining);
            if (cancelled > 0) {
                MemberBrandBenefitLedgerCommand.QuantityChange change = new MemberBrandBenefitLedgerCommand.QuantityChange();
                change.setRightId(right.getId()); change.setDelta(-cancelled); change.setPickedDelta(0);
                change.setAction("REFUND_CANCEL"); change.setRequestNo(command.getRequestNo() + "-" + right.getId());
                change.setSourceType("TRADE_REFUND"); change.setSourceNo(command.getSourceOrderNo());
                change.setOperatorName(command.getOperatorName()); change.setReason("DEFERRED_QUANTITY_REFUND");
                changeQuantity(change);
            }
            MemberQuantityRefundResult result = new MemberQuantityRefundResult();
            result.setSourceOrderDetailId(line.getSourceOrderDetailId()); result.setGoodsId(right.getGoodsId());
            result.setDeferredQuantity(true); result.setCancelledUnpickedQuantity(cancelled);
            result.setPickedReturnQuantity(line.getQuantity() - cancelled); results.add(result);
        }
        return results;
    }

    private UmsBrandBenefitTier enabledTier(String brandId, String tierCode) {
        UmsBrandBenefitTier tier = tierMapper.selectOne(new LambdaQueryWrapper<UmsBrandBenefitTier>()
                .eq(UmsBrandBenefitTier::getBrandId, brandId).eq(UmsBrandBenefitTier::getTierCode, tierCode)
                .eq(UmsBrandBenefitTier::getEnabled, true));
        require(tier != null, "品牌权益档位不存在或未启用"); return tier;
    }
    private UmsMemberQuantityRightLog quantityLog(Long id, String action, int delta, int before, int after, String request, String sourceType, String sourceNo, String operator, String reason) {
        UmsMemberQuantityRightLog log = new UmsMemberQuantityRightLog(); log.setRightId(id); log.setAction(action); log.setQuantityDelta(delta); log.setBeforeQuantity(before); log.setAfterQuantity(after); log.setRequestNo(request); log.setSourceType(blank(sourceType)); log.setSourceNo(sourceNo); log.setOperatorName(blank(operator)); log.setReason(blank(reason)); return log;
    }
    private UmsMemberAmountRightLog amountLog(Long id, String action, BigDecimal delta, BigDecimal before, BigDecimal after, String request, String sourceType, String sourceNo, String operator, String reason) {
        UmsMemberAmountRightLog log = new UmsMemberAmountRightLog(); log.setRightId(id); log.setAction(action); log.setAmountDelta(delta); log.setBeforeAmount(before); log.setAfterAmount(after); log.setRequestNo(request); log.setSourceType(blank(sourceType)); log.setSourceNo(sourceNo); log.setOperatorName(blank(operator)); log.setReason(blank(reason)); return log;
    }
    private UmsMemberTargetProgressLog targetLog(Long id, String action, BigDecimal delta, BigDecimal before, BigDecimal after, String request, String sourceType, String sourceNo, String operator, String reason) {
        UmsMemberTargetProgressLog log = new UmsMemberTargetProgressLog(); log.setPlanId(id); log.setAction(action); log.setAmountDelta(delta); log.setBeforeAmount(before); log.setAfterAmount(after); log.setRequestNo(request); log.setSourceType(blank(sourceType)); log.setSourceNo(sourceNo); log.setOperatorName(blank(operator)); log.setReason(blank(reason)); return log;
    }
    private static boolean positive(Integer value) { return value != null && value > 0; }
    private static boolean positive(BigDecimal value) { return value != null && value.compareTo(BigDecimal.ZERO) > 0; }
    private static boolean text(String value) { return StringUtils.hasText(value); }
    private static String blank(String value) { return value == null ? "" : value; }
    private static void require(boolean condition, String message) { if (!condition) throw new BaseException(message); }
}
