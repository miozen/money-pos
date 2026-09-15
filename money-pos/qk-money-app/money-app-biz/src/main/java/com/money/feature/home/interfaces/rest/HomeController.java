package com.money.feature.home.interfaces.rest;

import com.money.feature.home.application.DecisionEngineService;
import com.money.feature.home.application.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Home", description = "首页看板与智能决策大盘")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final DecisionEngineService decisionEngineService;

    @Operation(summary = "获取全维度经营大盘与智能决策警报 (新版引擎驱动)")
    @GetMapping("/count")
    public Map<String, Object> homeCountVO() {
        return decisionEngineService.getComprehensiveDashboard();
    }

    @Operation(summary = "大屏图表聚合数据")
    @GetMapping("/charts")
    public com.money.dto.Home.HomeChartsVO getChartsData(
            @RequestParam(required = false, defaultValue = "today") String timeRange) {
        // 🌟 传入前端点选的时间范围 (today, month, year, total)
        return homeService.getChartsData(timeRange);
    }
}
