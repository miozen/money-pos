package com.money.contract.member;

import java.util.List;

public interface MemberQuantityRefundCommandHandler {
    List<MemberQuantityRefundResult> handle(MemberQuantityRefundCommand command);
}
