package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.HomeDailyMemberQuery;
import com.money.entity.UmsMember;
import com.money.mapper.UmsMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** UMS implementation of HOME's legacy closed-day new-member count. */
@Service
@RequiredArgsConstructor
class HomeDailyMemberQueryService implements HomeDailyMemberQuery {
    private final UmsMemberMapper memberMapper;

    @Override
    public int countNewMembers(LocalDate date) {
        LocalDateTime startOfDay = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(date, LocalTime.MAX);
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<UmsMember>()
                .ge(UmsMember::getCreateTime, startOfDay)
                .le(UmsMember::getCreateTime, endOfDay));
        return count == null ? 0 : count.intValue();
    }
}
