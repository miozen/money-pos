package com.money.feature.fin.interfaces.rest;

import com.money.dto.Finance.FinanceWaterfallQueryDTO;
import com.money.dto.Finance.FinanceWaterfallVO;
import com.money.feature.fin.application.report.FinanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "财务报表大屏")
@RestController
@RequestMapping("/finance/report")
@RequiredArgsConstructor
public class FinanceReportController {

    private final FinanceReportService financeReportService;

    @Operation(summary = "财务瀑布流全口径日结表")
    @GetMapping("/waterfall/daily")
    public List<FinanceWaterfallVO> getDailyWaterfallReport(FinanceWaterfallQueryDTO queryDTO) {
        // 🌟 核心修复：抛弃多余的 Result 包装，直接返回最纯粹的 List 数据
        // 底层 AOP 会自动拦截并封装成前端需要的 JSON 格式
        return financeReportService.getDailyWaterfallReport(queryDTO);
    }
}