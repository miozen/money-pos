package com.money.contract.member;
import com.money.dto.memberbenefit.MemberBenefitTradeHistoryVO;
/** Entity-free TRADE history snapshot for the member profile. */
public interface MemberBenefitTradeHistoryQuery { MemberBenefitTradeHistoryVO listByMember(Long memberId); }
