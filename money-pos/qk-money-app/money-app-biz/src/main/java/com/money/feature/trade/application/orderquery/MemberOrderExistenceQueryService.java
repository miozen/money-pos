package com.money.feature.trade.application.orderquery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.trade.MemberOrderExistenceQuery;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.mapper.OmsOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** TRADE-owned existence check; it deliberately exposes no order Entity or detail to UMS. */
@Service
@RequiredArgsConstructor
class MemberOrderExistenceQueryService implements MemberOrderExistenceQuery {

    private final OmsOrderMapper omsOrderMapper;

    @Override
    public boolean hasOrderForMemberId(Long memberId) {
        return countOrdersForMemberId(memberId) > 0;
    }

    @Override
    public long countOrdersForMemberId(Long memberId) {
        if (memberId == null) return 0;
        Long count = omsOrderMapper.selectCount(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getMemberId, memberId));
        return count == null ? 0 : count;
    }
}
