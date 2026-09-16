package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberCheckoutQuery;
import com.money.contract.member.MemberCheckoutSnapshot;
import com.money.entity.UmsMember;
import com.money.mapper.UmsMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** UMS 对收银会员核验查询契约的实现。 */
@Service
@RequiredArgsConstructor
class MemberCheckoutQueryService implements MemberCheckoutQuery {

    private final UmsMemberMapper umsMemberMapper;

    @Override
    public MemberCheckoutSnapshot findActiveMemberById(Long memberId) {
        if (memberId == null) return null;
        UmsMember member = umsMemberMapper.selectOne(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getId, memberId)
                .eq(UmsMember::getDeleted, false));
        if (member == null) return null;
        return new MemberCheckoutSnapshot(member.getId(), member.getName(), member.getPhone());
    }
}
