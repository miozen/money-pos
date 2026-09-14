package com.money.feature.fin.application.analysis;

import com.money.constant.FinancialMetric;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.mapper.OmsOrderAnalysisMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.feature.fin.application.analysis.FinanceProfitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🌟 利润分析服务 (V6.0 荣耀扫荡版 - 语法修复)
 */
@Service
@RequiredArgsConstructor
public class FinanceProfitServiceImpl implements FinanceProfitService {

    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderAnalysisMapper omsOrderAnalysisMapper;

    @Override
    public List<ProfitRankVO> getProfitRanking() {
        LocalDateTime startTime = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);
        return omsOrderDetailMapper.getProfitRankingData(startTime);
    }

    @Override
    public List<CampaignReviewVO> getCampaignReview() {
        // 设置默认统计范围（最近 90 天）
        LocalDateTime startTime = LocalDateTime.now().minusMonths(3).with(LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.now().with(LocalTime.MAX);

        // 直接获取 SQL 聚合后的 ROI 数据
        List<com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO> roiData =
                omsOrderAnalysisMapper.getMarketingRoiStats(startTime, endTime);

        return roiData.stream().map(roi -> {
                    BigDecimal revenue = roi.getTotalRevenueBrought() != null ? roi.getTotalRevenueBrought() : BigDecimal.ZERO;
                    BigDecimal discount = roi.getTotalDiscountGived() != null ? roi.getTotalDiscountGived() : BigDecimal.ZERO;
                    BigDecimal roiMultiplier = BigDecimal.ZERO;

                    if (discount.compareTo(BigDecimal.ZERO) > 0) {
                        roiMultiplier = revenue.divide(discount, 2, RoundingMode.HALF_UP);
                    }

                    // 🌟 核心修复：对齐您 DTO 的 5 参数构造函数
                    // 参数顺序：1.名称, 2.使用次数, 3.优惠总额, 4.带来营收, 5.ROI倍数
                    return new CampaignReviewVO(
                            roi.getRuleName(),
                            roi.getUsedCount(),
                            discount,
                            revenue,
                            roiMultiplier
                    );
                })
                .sorted((a, b) -> b.getRoiMultiplier().compareTo(a.getRoiMultiplier()))
                .collect(Collectors.toList());
    }
}