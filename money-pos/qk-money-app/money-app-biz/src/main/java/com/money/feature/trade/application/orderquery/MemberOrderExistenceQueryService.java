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
        return memberId != null && omsOrderMapper.exists(new LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getMemberId, memberId));
    }
}
