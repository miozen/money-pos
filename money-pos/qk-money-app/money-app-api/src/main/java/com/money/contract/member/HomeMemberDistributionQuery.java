package com.money.contract.member;

import java.util.List;

/** UMS-owned current membership-level distribution used by the HOME chart. */
public interface HomeMemberDistributionQuery {

    List<HomeMemberDistributionSnapshot> listActiveMemberDistribution();
}
