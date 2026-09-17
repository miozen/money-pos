package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned inputs for FIN profit ranking and campaign review. */
public interface FinanceProfitQuery {
    List<FinanceProfitRankingSnapshot> listProfitRankings(LocalDateTime startInclusive);
    List<FinanceCampaignReviewSnapshot> listCampaignReviews(LocalDateTime startInclusive, LocalDateTime endInclusive);
}
