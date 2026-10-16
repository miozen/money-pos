package com.money.contract.trade;

/** Narrow TRADE-owned query used before UMS removes an erroneous empty member profile. */
public interface MemberOrderExistenceQuery {

    boolean hasOrderForMemberId(Long memberId);

    long countOrdersForMemberId(Long memberId);
}
