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
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final InventoryValuationQuery inventoryValuationQuery;
    private final HomeOrderReadQuery homeOrderReadQuery;
    private final HomeMemberDistributionQuery homeMemberDistributionQuery;

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
        OrderCountVO vo = new OrderCountVO();
        vo.setOrderCount(snapshot.getOrderCount());
        vo.setSaleCount(snapshot.getSaleCount());
        vo.setCostCount(snapshot.getCostCount());
        vo.setProfit(snapshot.getProfit());
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

        chartsVO.setTrendData(toTrendChartData(homeOrderReadQuery.listSalesTrend(trendStartTime, endTime)));
        chartsVO.setPieData(toBrandPieData(homeOrderReadQuery.listBrandSales(startTime, endTime)));

        // 会员等级是即时状态（总资产），不跟时间联动
        chartsVO.setBarData(toMemberBarData(homeMemberDistributionQuery.listActiveMemberDistribution()));

        return chartsVO;
    }

    private List<TrendChartVO> toTrendChartData(List<HomeSalesTrendSnapshot> snapshots) {
        return snapshots.stream().map(snapshot -> {
            TrendChartVO point = new TrendChartVO();
            point.setDate(snapshot.getDate());
            point.setSales(snapshot.getSales());
            point.setProfit(snapshot.getProfit());
            return point;
        }).collect(Collectors.toList());
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
