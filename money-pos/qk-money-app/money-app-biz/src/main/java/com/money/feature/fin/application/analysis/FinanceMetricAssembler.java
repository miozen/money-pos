package com.money.feature.fin.application.analysis;

import com.money.contract.trade.FinanceOperatingMetricSnapshot;
import com.money.contract.trade.FinanceDashboardMemberDailySnapshot;
import com.money.dto.OmsOrder.OmsSalesDataVO.*;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.util.MoneyUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🌟 财务指标装配器：专职负责数据的聚合、计算与图表格式化
 * 让 Service 和 Mapper 彻底解脱！
 */
@Component
public class FinanceMetricAssembler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 组装销售大盘基础图表数据
     */
    public void assembleBasicDashboard(SalesDashboardVO vo, List<FinanceOperatingMetricSnapshot> dailyStats, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, FinanceOperatingMetricSnapshot> statsMap = dailyStats.stream()
                .collect(Collectors.toMap(FinanceOperatingMetricSnapshot::getPeriod, stat -> stat, (existing, replacement) -> existing));

        BigDecimal totalSalesAmount = BigDecimal.ZERO;
        int totalOrderCount = 0;
        int totalGoodsCount = 0;

        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendSales = new ArrayList<>();
        List<Integer> trendOrders = new ArrayList<>();
        List<BigDecimal> trendAsp = new ArrayList<>();

        // 🌟 按天补齐 0，确保前端折线图不会断层
        for (LocalDate date = startTime.toLocalDate(); !date.isAfter(endTime.toLocalDate()); date = date.plusDays(1)) {
            String dbPeriodKey = date.toString(); // 格式 "yyyy-MM-dd"
            trendDates.add(date.format(DATE_FORMATTER));

            FinanceOperatingMetricSnapshot dailyStat = statsMap.get(dbPeriodKey);
            if (dailyStat != null) {
                totalSalesAmount = totalSalesAmount.add(dailyStat.getNetSalesAmount());
                if (dailyStat.getNetSalesAmount().compareTo(BigDecimal.ZERO) > 0) {
                totalOrderCount += (int) dailyStat.getOrderCount();
                }
                totalGoodsCount += (int) dailyStat.getGoodsCount();

                trendSales.add(dailyStat.getNetSalesAmount());
                trendOrders.add((int) dailyStat.getOrderCount());

                BigDecimal asp = dailyStat.getOrderCount() > 0
                        ? dailyStat.getNetSalesAmount().divide(BigDecimal.valueOf(dailyStat.getOrderCount()), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                trendAsp.add(asp);
            } else {
                trendSales.add(BigDecimal.ZERO);
                trendOrders.add(0);
                trendAsp.add(BigDecimal.ZERO);
            }
        }

        vo.setTotalSalesAmount(totalSalesAmount);
        vo.setTotalOrderCount(totalOrderCount);
        vo.setTotalGoodsCount(totalGoodsCount);
        vo.setAvgOrderValue(totalOrderCount > 0 ? totalSalesAmount.divide(new BigDecimal(totalOrderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        vo.setTrendDates(trendDates);
        vo.setTrendSales(trendSales);
        vo.setTrendOrders(trendOrders);
        vo.setTrendAsp(trendAsp);
    }

    /**
     * 组装会员 vs 散客双线趋势图
     */
    public MemberTrendVO assembleMemberTrend(List<FinanceDashboardMemberDailySnapshot> memberStats, LocalDateTime startTime, LocalDateTime endTime) {
        MemberTrendVO memberTrendVO = new MemberTrendVO();
        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> memberSales = new ArrayList<>();
        List<BigDecimal> guestSales = new ArrayList<>();
        List<BigDecimal> memberAsp = new ArrayList<>();
        List<BigDecimal> guestAsp = new ArrayList<>();

        for (LocalDate date = startTime.toLocalDate(); !date.isAfter(endTime.toLocalDate()); date = date.plusDays(1)) {
            String matchDate = date.toString();
            trendDates.add(date.format(DATE_FORMATTER));

            BigDecimal mSales = BigDecimal.ZERO, gSales = BigDecimal.ZERO;
            int mOrders = 0, gOrders = 0;

            for (FinanceDashboardMemberDailySnapshot stat : memberStats) {
                if (matchDate.equals(stat.getDate())) {
                    if (stat.isMember()) {
                        mSales = mSales.add(stat.getSalesAmount() != null ? stat.getSalesAmount() : BigDecimal.ZERO);
                        mOrders += (int) stat.getOrderCount();
                    } else {
                        gSales = gSales.add(stat.getSalesAmount() != null ? stat.getSalesAmount() : BigDecimal.ZERO);
                        gOrders += (int) stat.getOrderCount();
                    }
                }
            }

            memberSales.add(mSales);
            guestSales.add(gSales);
            memberAsp.add(mOrders > 0 ? mSales.divide(new BigDecimal(mOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            guestAsp.add(gOrders > 0 ? gSales.divide(new BigDecimal(gOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }

        memberTrendVO.setDates(trendDates);
        memberTrendVO.setMemberSales(memberSales);
        memberTrendVO.setGuestSales(guestSales);
        memberTrendVO.setMemberAsp(memberAsp);
        memberTrendVO.setGuestAsp(guestAsp);

        return memberTrendVO;
    }

    /**
     * 计算营销 ROI
     */
    public List<MarketingRoiVO> calculateMarketingRoi(List<MarketingRoiVO> results) {
        for (MarketingRoiVO vo : results) {
            BigDecimal revenue = vo.getTotalRevenueBrought() != null ? vo.getTotalRevenueBrought() : BigDecimal.ZERO;
            BigDecimal cost = vo.getTotalDiscountGived() != null ? vo.getTotalDiscountGived() : BigDecimal.ZERO;
            int usedCount = vo.getUsedCount() != null ? vo.getUsedCount() : 0;

            vo.setRoiMultiplier(cost.compareTo(BigDecimal.ZERO) > 0 ? revenue.divide(cost, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            vo.setAvgOrderValue(usedCount > 0 ? revenue.divide(new BigDecimal(usedCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }
        results.sort((a, b) -> b.getRoiMultiplier().compareTo(a.getRoiMultiplier()));
        return results;
    }

    /**
     * 汇总全局销售额、成本与利润
     */
    public OrderCountVO aggregateTotalMetrics(List<FinanceOperatingMetricSnapshot> stats) {
        OrderCountVO vo = new OrderCountVO();
        long totalOrder = 0;
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (FinanceOperatingMetricSnapshot stat : stats) {
            totalOrder += stat.getOrderCount();
            totalSales = MoneyUtil.add(totalSales, stat.getNetSalesAmount());
            totalCost = MoneyUtil.add(totalCost, stat.getCostAmount());
        }

        vo.setOrderCount(totalOrder);
        vo.setTotalSales(totalSales);
        vo.setSaleCount(totalSales);
        vo.setCostCount(totalCost);
        vo.setProfit(MoneyUtil.subtract(totalSales, totalCost));
        return vo;
    }

    /**
     * 组装单品连续销量趋势
     */
    public List<GoodsTrendVO> assembleGoodsTrend(List<DailyGoodsStatDTO> rawStats, List<Long> goodsIds, LocalDateTime startTime, LocalDateTime endTime) {
        if (goodsIds == null || goodsIds.isEmpty()) return new ArrayList<>();

        Map<Long, GoodsTrendVO> resultMap = new HashMap<>();
        for (Long id : goodsIds) {
            GoodsTrendVO vo = new GoodsTrendVO();
            vo.setGoodsId(id);
            vo.setGoodsName("商品 ID:" + id);
            vo.setTrendSalesQty(new ArrayList<>());
            resultMap.put(id, vo);
        }

        for (LocalDate date = startTime.toLocalDate(); !date.isAfter(endTime.toLocalDate()); date = date.plusDays(1)) {
            String matchDate = date.toString();

            for (Long goodsId : goodsIds) {
                GoodsTrendVO vo = resultMap.get(goodsId);
                int dailyQty = 0;

                for (DailyGoodsStatDTO stat : rawStats) {
                    if (stat.getGoodsId().equals(goodsId)) {
                        vo.setGoodsName(stat.getGoodsName());
                        if (matchDate.equals(stat.getDateStr())) {
                            dailyQty += (stat.getSalesQty() != null ? stat.getSalesQty() : 0);
                        }
                    }
                }
                vo.getTrendSalesQty().add(dailyQty);
            }
        }
        return new ArrayList<>(resultMap.values());
    }
}
