package com.money.feature.fin.application.analysis;

import com.money.dto.Finance.FinanceDataVO.*;
import java.util.List;

public interface FinanceProfitService {
    List<ProfitRankVO> getProfitRanking();
    List<CampaignReviewVO> getCampaignReview();
}