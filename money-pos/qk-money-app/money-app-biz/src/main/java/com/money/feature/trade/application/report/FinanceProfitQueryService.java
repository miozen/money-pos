package com.money.feature.trade.application.report;

import com.money.contract.trade.FinanceCampaignReviewSnapshot;
import com.money.contract.trade.FinanceProfitQuery;
import com.money.contract.trade.FinanceProfitRankingSnapshot;
import com.money.dto.Finance.FinanceDataVO.ProfitRankVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.mapper.OmsOrderAnalysisMapper;
import com.money.mapper.OmsOrderDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** TRADE implementation preserving the independent profit-ranking and campaign-review formulas. */
@Service
@RequiredArgsConstructor
class FinanceProfitQueryService implements FinanceProfitQuery {
    private final OmsOrderDetailMapper orderDetailMapper;
    private final OmsOrderAnalysisMapper orderAnalysisMapper;

    @Override
    public List<FinanceProfitRankingSnapshot> listProfitRankings(LocalDateTime startInclusive) {
        return orderDetailMapper.getProfitRankingData(startInclusive).stream()
                .map(row -> new FinanceProfitRankingSnapshot(row.getGoodsName(), intValue(row.getTotalQuantity()),
                        zero(row.getTotalSales()), zero(row.getTotalProfit())))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceCampaignReviewSnapshot> listCampaignReviews(LocalDateTime startInclusive,
                                                                     LocalDateTime endInclusive) {
        return orderAnalysisMapper.getMarketingRoiStats(startInclusive, endInclusive).stream()
                .map(row -> new FinanceCampaignReviewSnapshot(row.getRuleName(), row.getRuleType(), intValue(row.getUsedCount()),
                        zero(row.getTotalDiscountGived()), zero(row.getTotalRevenueBrought())))
                .collect(Collectors.toList());
    }

    private int intValue(Integer value) { return value == null ? 0 : value; }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
