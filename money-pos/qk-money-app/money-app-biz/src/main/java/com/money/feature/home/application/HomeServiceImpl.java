package com.money.feature.home.application;

import com.money.contract.goods.InventoryValuationQuery;
import com.money.contract.member.HomeMemberDistributionQuery;
import com.money.contract.member.HomeMemberDistributionSnapshot;
import com.money.contract.trade.HomeBrandSalesSnapshot;
import com.money.contract.trade.HomeOrderReadQuery;
import com.money.contract.trade.HomeOrderReadSnapshot;
import com.money.contract.trade.HomeSalesTrendSnapshot;
import com.money.dto.Home.BrandPieVO;
import com.money.dto.Home.HomeCountVO;
import com.money.dto.Home.TrendChartVO;
import com.money.dto.OmsOrder.OrderCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Year;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {
    private static final DateTimeFormatter TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MONTH_TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM月");

    private final InventoryValuationQuery inventoryValuationQuery;
    private final HomeOrderReadQuery homeOrderReadQuery;
    private final HomeMemberDistributionQuery homeMemberDistributionQuery;
    private final HomeNonProductReceiptProjection nonProductReceiptProjection;

    @Override
    public HomeCountVO homeCount() {
        HomeCountVO homeCountVO = new HomeCountVO();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        homeCountVO.setToday(this.executeAggregateQuery(todayStart, tomorrowStart));

        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);
        homeCountVO.setMonth(this.executeAggregateQuery(monthStart, nextMonthStart));

        LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
        LocalDateTime nextYearStart = yearStart.plusYears(1);
        homeCountVO.setYear(this.executeAggregateQuery(yearStart, nextYearStart));

        homeCountVO.setTotal(this.executeAggregateQuery(null, null));
        homeCountVO.setInventoryValue(inventoryValuationQuery.getCurrentStockValue());

        return homeCountVO;
    }

    private OrderCountVO executeAggregateQuery(LocalDateTime startTime, LocalDateTime endTime) {
        HomeOrderReadSnapshot snapshot = homeOrderReadQuery.summarizeHomeCount(startTime, endTime);
        BigDecimal nonProductCollection = nonProductReceiptProjection.collectionFor(startTime, endTime);
        OrderCountVO vo = new OrderCountVO();
        vo.setOrderCount(snapshot.getOrderCount());
        vo.setSaleCount(snapshot.getSaleCount().add(nonProductCollection));
        vo.setCostCount(snapshot.getCostCount());
        vo.setProfit(snapshot.getProfit().add(nonProductCollection));
        return vo;
    }

    // 🌟 图表引擎：动态计算时间范围
    @Override
    public com.money.dto.Home.HomeChartsVO getChartsData(String timeRange) {
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;

        if ("today".equals(timeRange)) {
            startTime = LocalDate.now().atStartOfDay();
            endTime = startTime.plusDays(1);
        } else if ("month".equals(timeRange)) {
            startTime = YearMonth.now().atDay(1).atStartOfDay();
            endTime = startTime.plusMonths(1);
        } else if ("year".equals(timeRange)) {
            startTime = Year.now().atDay(1).atStartOfDay();
            endTime = startTime.plusYears(1);
        }

        com.money.dto.Home.HomeChartsVO chartsVO = new com.money.dto.Home.HomeChartsVO();

        // 🌟 走势图智能处理：如果是今天，强行降级展示近7天（因为只展示当天的1个点没有意义）
        LocalDateTime trendStartTime = startTime;
        if ("today".equals(timeRange)) {
            trendStartTime = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
            endTime = null; // 查到最新
        }

        List<HomeSalesTrendSnapshot> trend = mergeNonProductTrend(
                homeOrderReadQuery.listSalesTrend(trendStartTime, endTime), trendStartTime, endTime);
        TrendGranularity granularity = trendGranularityFor(timeRange);
        if (granularity == TrendGranularity.DAILY) {
            trend = completeDailyTrend(trend, trendStartTime.toLocalDate(), LocalDate.now());
        } else if (granularity == TrendGranularity.MONTHLY) {
            trend = completeMonthlyTrend(aggregateTrend(trend, granularity), YearMonth.from(trendStartTime), YearMonth.now());
        } else {
            trend = aggregateTrend(trend, granularity);
        }
        chartsVO.setTrendData(toTrendChartData(trend, granularity));
        chartsVO.setPieData(toBrandPieData(homeOrderReadQuery.listBrandSales(startTime, endTime)));

        // 会员等级是即时状态（总资产），不跟时间联动
        chartsVO.setBarData(toMemberBarData(homeMemberDistributionQuery.listActiveMemberDistribution()));

        return chartsVO;
    }

    private TrendGranularity trendGranularityFor(String timeRange) {
        if ("year".equals(timeRange)) {
            return TrendGranularity.MONTHLY;
        }
        if ("total".equals(timeRange)) {
            return TrendGranularity.YEARLY;
        }
        return TrendGranularity.DAILY;
    }

    private List<TrendChartVO> toTrendChartData(List<HomeSalesTrendSnapshot> snapshots,
                                                  TrendGranularity granularity) {
        return snapshots.stream().map(snapshot -> {
            TrendChartVO point = new TrendChartVO();
            point.setDate(formatTrendLabel(snapshot.getDate(), granularity));
            point.setSales(snapshot.getSales());
            point.setProfit(snapshot.getProfit());
            return point;
        }).collect(Collectors.toList());
    }

    private String formatTrendLabel(String bucket, TrendGranularity granularity) {
        if (granularity == TrendGranularity.MONTHLY) {
            return YearMonth.parse(bucket).format(MONTH_TREND_LABEL_FORMATTER);
        }
        if (granularity == TrendGranularity.YEARLY) {
            return bucket;
        }
        return LocalDate.parse(bucket).format(TREND_LABEL_FORMATTER);
    }

    private List<HomeSalesTrendSnapshot> mergeNonProductTrend(List<HomeSalesTrendSnapshot> orderTrend,
                                                                LocalDateTime startInclusive, LocalDateTime endExclusive) {
        Map<String, BigDecimal[]> totals = new TreeMap<>();
        for (HomeSalesTrendSnapshot point : orderTrend) {
            totals.put(point.getDate(), new BigDecimal[]{point.getSales(), point.getProfit()});
        }
        nonProductReceiptProjection.listFor(startInclusive, endExclusive).forEach(receipt -> {
            String date = receipt.getDate().toString();
            BigDecimal[] values = totals.computeIfAbsent(date, ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            values[0] = values[0].add(receipt.getCollectionAmount());
            values[1] = values[1].add(receipt.getCollectionAmount());
        });
        return totals.entrySet().stream().map(entry -> new HomeSalesTrendSnapshot(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .collect(Collectors.toList());
    }

    private List<HomeSalesTrendSnapshot> completeDailyTrend(List<HomeSalesTrendSnapshot> trend,
                                                             LocalDate startInclusive, LocalDate endInclusive) {
        Map<String, HomeSalesTrendSnapshot> pointsByDate = trend.stream()
                .collect(Collectors.toMap(HomeSalesTrendSnapshot::getDate, point -> point));
        List<HomeSalesTrendSnapshot> completeTrend = new ArrayList<>();
        for (LocalDate date = startInclusive; !date.isAfter(endInclusive); date = date.plusDays(1)) {
            completeTrend.add(pointsByDate.getOrDefault(date.toString(),
                    new HomeSalesTrendSnapshot(date.toString(), BigDecimal.ZERO, BigDecimal.ZERO)));
        }
        return completeTrend;
    }

    private List<HomeSalesTrendSnapshot> aggregateTrend(List<HomeSalesTrendSnapshot> trend,
                                                         TrendGranularity granularity) {
        Map<String, BigDecimal[]> totals = new TreeMap<>();
        for (HomeSalesTrendSnapshot point : trend) {
            LocalDate date = LocalDate.parse(point.getDate());
            String bucket = granularity == TrendGranularity.MONTHLY
                    ? YearMonth.from(date).toString()
                    : String.valueOf(date.getYear());
            BigDecimal[] values = totals.computeIfAbsent(bucket,
                    ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            values[0] = values[0].add(point.getSales());
            values[1] = values[1].add(point.getProfit());
        }
        return totals.entrySet().stream()
                .map(entry -> new HomeSalesTrendSnapshot(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .collect(Collectors.toList());
    }

    private List<HomeSalesTrendSnapshot> completeMonthlyTrend(List<HomeSalesTrendSnapshot> trend,
                                                               YearMonth startInclusive, YearMonth endInclusive) {
        Map<String, HomeSalesTrendSnapshot> pointsByMonth = trend.stream()
                .collect(Collectors.toMap(HomeSalesTrendSnapshot::getDate, point -> point));
        List<HomeSalesTrendSnapshot> completeTrend = new ArrayList<>();
        for (YearMonth month = startInclusive; !month.isAfter(endInclusive); month = month.plusMonths(1)) {
            String bucket = month.toString();
            completeTrend.add(pointsByMonth.getOrDefault(bucket,
                    new HomeSalesTrendSnapshot(bucket, BigDecimal.ZERO, BigDecimal.ZERO)));
        }
        return completeTrend;
    }

    private enum TrendGranularity {
        DAILY,
        MONTHLY,
        YEARLY
    }

    private List<BrandPieVO> toBrandPieData(List<HomeBrandSalesSnapshot> snapshots) {
        return snapshots.stream().map(snapshot -> {
            BrandPieVO point = new BrandPieVO();
            point.setName(snapshot.getName());
            point.setValue(snapshot.getValue());
            return point;
        }).collect(Collectors.toList());
    }

    private List<com.money.dto.Home.MemberBarVO> toMemberBarData(List<HomeMemberDistributionSnapshot> snapshots) {
        return snapshots.stream().map(snapshot -> {
            com.money.dto.Home.MemberBarVO point = new com.money.dto.Home.MemberBarVO();
            point.setBrandName(snapshot.getBrandName());
            point.setLevelCode(snapshot.getLevelCode());
            point.setCount(snapshot.getCount());
            return point;
        }).collect(Collectors.toList());
    }
}
