package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.trade.MemberOrderExistenceQuery;
import com.money.dto.UmsMember.MemberMergePreviewVO;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberAmountRight;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberQuantityRight;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberTargetPlan;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberAmountRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberQuantityRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberTargetPlanMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Read-only UMS merge eligibility assessment; execution remains intentionally unavailable. */
@Service
@RequiredArgsConstructor
public class MemberMergePreviewService {
    private final UmsMemberMapper memberMapper;
    private final PosMemberCouponMapper couponMapper;
    private final UmsMemberQuantityRightMapper quantityRightMapper;
    private final UmsMemberAmountRightMapper amountRightMapper;
    private final UmsMemberTargetPlanMapper targetPlanMapper;
    private final MemberOrderExistenceQuery orderExistenceQuery;

    public MemberMergePreviewVO preview(Long sourceMemberId, Long targetMemberId) {
        if (sourceMemberId == null || targetMemberId == null || sourceMemberId.equals(targetMemberId)) throw new BaseException("请选择两个不同的有效会员");
        UmsMember source = active(sourceMemberId); UmsMember target = active(targetMemberId);
        MemberMergePreviewVO result = new MemberMergePreviewVO();
        result.setSource(summary(source)); result.setTarget(summary(target));
        result.setSourceOrderCount(orderExistenceQuery.countOrdersForMemberId(sourceMemberId));
        result.setSourceUnusedCouponCount(couponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>().eq(PosMemberCoupon::getMemberId, sourceMemberId).eq(PosMemberCoupon::getStatus, "UNUSED")));
        List<String> blockers = new ArrayList<>();
        if (quantityRightMapper.exists(new LambdaQueryWrapper<UmsMemberQuantityRight>().eq(UmsMemberQuantityRight::getMemberId, sourceMemberId))) blockers.add("来源会员存在数量权益");
        if (amountRightMapper.exists(new LambdaQueryWrapper<UmsMemberAmountRight>().eq(UmsMemberAmountRight::getMemberId, sourceMemberId))) blockers.add("来源会员存在金额权益");
        if (targetPlanMapper.exists(new LambdaQueryWrapper<UmsMemberTargetPlan>().eq(UmsMemberTargetPlan::getMemberId, sourceMemberId))) blockers.add("来源会员存在TARGET计划");
        result.setBlockers(blockers); result.setMergeAllowed(blockers.isEmpty()); return result;
    }
    private UmsMember active(Long id) { UmsMember m = memberMapper.selectOne(new LambdaQueryWrapper<UmsMember>().eq(UmsMember::getId, id).eq(UmsMember::getDeleted, false)); if (m == null) throw new BaseException("会员不存在或不可合并"); return m; }
    private MemberMergePreviewVO.MemberSummary summary(UmsMember m) { MemberMergePreviewVO.MemberSummary s = new MemberMergePreviewVO.MemberSummary(); s.setId(m.getId()); s.setName(m.getName()); s.setPhone(m.getPhone()); s.setBalance(m.getBalance()); s.setCoupon(m.getCoupon()); return s; }
}
