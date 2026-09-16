package com.money.feature.ums.application.member;

import com.money.contract.member.MemberOrderProfileQuery;
import com.money.contract.member.MemberOrderProfileSnapshot;
import com.money.dto.UmsMember.UmsMemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** UMS 对订单会员档案查询契约的实现。 */
@Service
@RequiredArgsConstructor
class MemberOrderProfileQueryService implements MemberOrderProfileQuery {

    private final UmsMemberProfileService umsMemberProfileService;

    @Override
    public MemberOrderProfileSnapshot findByMemberId(Long memberId) {
        if (memberId == null) return null;
        try {
            UmsMemberVO member = umsMemberProfileService.getDetail(memberId);
            return new MemberOrderProfileSnapshot(member.getId(), member.getName(), member.getPhone(), member.getCoupon(),
                    member.getBrandLevels(), member.getBrandLevelDesc());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
