package com.money.contract.member;

import java.time.LocalDate;

/** UMS-owned new-member count used when HOME writes its daily snapshot. */
public interface HomeDailyMemberQuery {

    int countNewMembers(LocalDate date);
}
