package com.money.feature.home.application;

import com.money.dto.Home.HomeCountVO;

/**
 * @author : money
 * @version : 2.0.0
 * @description : 主页服务
 */
public interface HomeService {
    HomeCountVO homeCount();

    // 🌟 新增参数：接收前端传来的时间范围标识 (today, month, year, total)
    com.money.dto.Home.HomeChartsVO getChartsData(String timeRange);
}
