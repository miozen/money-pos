package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.context.TenantContextHolder;
import com.money.dto.UmsMember.MemberMergeCommandDTO;
import com.money.feature.ums.application.cloudsync.MemberCloudSyncOutboxService;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberLog;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberMerge;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberAmountRight;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberQuantityRight;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberTargetPlan;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberAmountRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberMergeMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberQuantityRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberTargetPlanMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The only writer for the duplicate-member merge audit and transfer state. */
@Service
@RequiredArgsConstructor
public class MemberMergeCommandService {
    private final UmsMemberMapper memberMapper;
    private final PosMemberCouponMapper couponMapper;
    private final UmsMemberLogMapper memberLogMapper;
    private final UmsMemberMergeMapper mergeMapper;
    private final UmsMemberQuantityRightMapper quantityRightMapper;
    private final UmsMemberAmountRightMapper amountRightMapper;
    private final UmsMemberTargetPlanMapper targetPlanMapper;
    private final MemberCloudSyncOutboxService cloudSyncOutboxService;

    @Transactional(rollbackFor = Exception.class)
    public void merge(MemberMergeCommandDTO command) {
        validate(command);
        Long tenantId = currentTenantId();
        UmsMemberMerge existing = mergeMapper.selectOne(new LambdaQueryWrapper<UmsMemberMerge>()
                .eq(UmsMemberMerge::getMergeNo, command.getRequestNo()));
        if (existing != null) {
            verifyReplay(existing, command);
            return;
        }

        Long firstId = Math.min(command.getSourceMemberId(), command.getTargetMemberId());
        Long secondId = Math.max(command.getSourceMemberId(), command.getTargetMemberId());
        UmsMember first = memberMapper.selectByIdForUpdate(firstId, tenantId);
        UmsMember second = memberMapper.selectByIdForUpdate(secondId, tenantId);
        if (first == null || second == null) throw new BaseException("会员不存在或不属于当前租户");
        UmsMember source = command.getSourceMemberId().equals(firstId) ? first : second;
        UmsMember target = command.getTargetMemberId().equals(firstId) ? first : second;

        existing = mergeMapper.selectOne(new LambdaQueryWrapper<UmsMemberMerge>()
                .eq(UmsMemberMerge::getMergeNo, command.getRequestNo()));
        if (existing != null) {
            verifyReplay(existing, command);
            return;
        }
        if (mergeMapper.exists(new LambdaQueryWrapper<UmsMemberMerge>().eq(UmsMemberMerge::getSourceMemberId, source.getId()))) {
            throw new BaseException("来源会员已经合并，不能再次合并");
        }
        if (Boolean.TRUE.equals(source.getDeleted()) || Boolean.TRUE.equals(target.getDeleted())) {
            throw new BaseException("会员不存在或不可合并");
        }
        requireTransferEligible(source);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal sourceBalance = amount(source.getBalance());
        BigDecimal targetBalance = amount(target.getBalance());
        long unusedCouponCount = couponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getMemberId, source.getId()).eq(PosMemberCoupon::getStatus, "UNUSED"));

        UmsMemberMerge audit = new UmsMemberMerge();
        audit.setMergeNo(command.getRequestNo().trim());
        audit.setSourceMemberId(source.getId()); audit.setTargetMemberId(target.getId());
        audit.setReason(command.getReason().trim()); audit.setSourceBalanceBefore(sourceBalance);
        audit.setTargetBalanceBefore(targetBalance); audit.setTransferredBalance(sourceBalance);
        audit.setTransferredUnusedCouponCount((int) unusedCouponCount); audit.setStatus("PENDING");
        audit.setOperatorName(operatorName()); audit.setCreateTime(now); audit.setTenantId(tenantId);
        mergeMapper.insert(audit);

        if (unusedCouponCount > 0) {
            couponMapper.update(null, new LambdaUpdateWrapper<PosMemberCoupon>()
                    .set(PosMemberCoupon::getMemberId, target.getId())
                    .eq(PosMemberCoupon::getMemberId, source.getId()).eq(PosMemberCoupon::getStatus, "UNUSED"));
        }
        UmsMember targetUpdate = new UmsMember(); targetUpdate.setId(target.getId()); targetUpdate.setBalance(targetBalance.add(sourceBalance));
        memberMapper.updateById(targetUpdate);
        UmsMember sourceUpdate = new UmsMember(); sourceUpdate.setId(source.getId()); sourceUpdate.setBalance(BigDecimal.ZERO);
        sourceUpdate.setDeleted(true); sourceUpdate.setMergedToMemberId(target.getId()); sourceUpdate.setMergedTime(now);
        memberMapper.updateById(sourceUpdate);
        writeBalanceLogs(source, target, sourceBalance, targetBalance, command.getRequestNo(), now);

        audit.setStatus("COMPLETED"); audit.setCompleteTime(now);
        mergeMapper.updateById(audit);
        cloudSyncOutboxService.markUpsert(target.getId());
        cloudSyncOutboxService.markTombstone(source.getId());
    }

    private void requireTransferEligible(UmsMember source) {
        if (amount(source.getCoupon()).compareTo(BigDecimal.ZERO) != 0) throw new BaseException("来源会员存在金额型券额度，请先人工处理");
        Long id = source.getId();
        if (quantityRightMapper.exists(new LambdaQueryWrapper<UmsMemberQuantityRight>().eq(UmsMemberQuantityRight::getMemberId, id))) throw new BaseException("来源会员存在数量权益，不能合并");
        if (amountRightMapper.exists(new LambdaQueryWrapper<UmsMemberAmountRight>().eq(UmsMemberAmountRight::getMemberId, id))) throw new BaseException("来源会员存在金额权益，不能合并");
        if (targetPlanMapper.exists(new LambdaQueryWrapper<UmsMemberTargetPlan>().eq(UmsMemberTargetPlan::getMemberId, id))) throw new BaseException("来源会员存在TARGET计划，不能合并");
    }

    private void writeBalanceLogs(UmsMember source, UmsMember target, BigDecimal transferred, BigDecimal targetBefore, String requestNo, LocalDateTime now) {
        if (transferred.compareTo(BigDecimal.ZERO) == 0) return;
        memberLogMapper.insert(log(source, transferred.negate(), BigDecimal.ZERO, requestNo, "重复会员合并转出至会员 " + target.getId(), now));
        memberLogMapper.insert(log(target, transferred, targetBefore.add(transferred), requestNo, "重复会员合并接收自会员 " + source.getId(), now));
    }

    private UmsMemberLog log(UmsMember member, BigDecimal change, BigDecimal after, String requestNo, String remark, LocalDateTime now) {
        UmsMemberLog log = new UmsMemberLog(); log.setMemberId(member.getId()); log.setMemberName(member.getName()); log.setMemberPhone(member.getPhone());
        log.setType("BALANCE"); log.setOperateType("MERGE"); log.setAmount(change); log.setRealAmount(BigDecimal.ZERO); log.setAfterAmount(after);
        log.setOrderNo(requestNo); log.setRemark(remark); log.setCreateTime(now); return log;
    }

    private void verifyReplay(UmsMemberMerge existing, MemberMergeCommandDTO command) {
        if (!existing.getSourceMemberId().equals(command.getSourceMemberId()) || !existing.getTargetMemberId().equals(command.getTargetMemberId())) {
            throw new BaseException("合并请求号已用于其他会员");
        }
    }
    private void validate(MemberMergeCommandDTO c) {
        if (c == null || c.getSourceMemberId() == null || c.getTargetMemberId() == null || c.getSourceMemberId().equals(c.getTargetMemberId())
                || blank(c.getRequestNo()) || blank(c.getReason())) throw new BaseException("合并请求不完整");
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private BigDecimal amount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private Long currentTenantId() { return TenantContextHolder.getTenant() == null ? 0L : TenantContextHolder.getTenant(); }
    private String operatorName() { return SecurityContextHolder.getContext().getAuthentication() == null ? "System" : SecurityContextHolder.getContext().getAuthentication().getName(); }
}
