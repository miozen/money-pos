package com.money.feature.home.application;

import com.money.contract.goods.InventoryValuationQuery;
import com.money.contract.trade.HomeOrderReadQuery;
import com.money.contract.trade.HomeOrderReadSnapshot;
import com.money.dto.Home.HomeCountVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final InventoryValuationQuery inventoryValuationQuery;
    private final HomeOrderReadQuery homeOrderReadQuery;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;

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

        // 动态穿透 SQL
        chartsVO.setTrendData(omsOrderDetailMapper.getTrendData(trendStartTime, endTime));
        chartsVO.setPieData(omsOrderDetailMapper.getBrandPieData(startTime, endTime));

        // 会员等级是即时状态（总资产），不跟时间联动
        chartsVO.setBarData(umsMemberBrandLevelMapper.getMemberBarData());

        return chartsVO;
    }
}
