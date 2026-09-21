package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberPosProfileQuery;
import com.money.contract.member.MemberPosProfileSnapshot;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** UMS 对 POS 会员搜索权益快照契约的实现。 */
@Service
@RequiredArgsConstructor
class MemberPosProfileQueryService implements MemberPosProfileQuery {

    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;

    @Override
    public List<MemberPosProfileSnapshot> searchActiveMembers(String keyword) {
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        List<UmsMember> members = umsMemberMapper.selectList(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getDeleted, false)
                .and(hasKeyword, wrapper -> wrapper.like(UmsMember::getName, keyword)
                        .or().like(UmsMember::getPhone, keyword)));
        if (members.isEmpty()) return new ArrayList<>();

        List<Long> memberIds = members.stream().map(UmsMember::getId).collect(Collectors.toList());
        Map<Long, List<UmsMemberBrandLevel>> levelsByMember = umsMemberBrandLevelMapper.selectList(
                new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds))
                .stream().collect(Collectors.groupingBy(UmsMemberBrandLevel::getMemberId));

        return members.stream().map(member -> {
            Map<String, String> brandLevels = new HashMap<>();
            for (UmsMemberBrandLevel level : levelsByMember.getOrDefault(member.getId(), new ArrayList<>())) {
                brandLevels.put(level.getBrand(), level.getLevelCode());
            }
            return new MemberPosProfileSnapshot(member.getId(), member.getCode(), member.getName(), member.getType(),
                    member.getPhone(), member.getCoupon(), member.getBalance(), member.getLevelId(), brandLevels);
        }).collect(Collectors.toList());
    }
}
