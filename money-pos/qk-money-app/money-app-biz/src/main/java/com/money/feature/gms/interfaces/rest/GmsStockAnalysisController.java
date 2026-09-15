package com.money.feature.gms.interfaces.rest;

import com.alibaba.excel.EasyExcel;
import com.money.dto.GmsGoods.GmsStockDataVO.StockAnalysisReportVO;
import com.money.feature.gms.application.stockanalysis.GmsStockAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

@Tag(name = "GmsStockAnalysis", description = "7.x 进销存及资产流失大屏分析")
@RestController
@RequestMapping("/gms/analysis")
@RequiredArgsConstructor
public class GmsStockAnalysisController {

    private final GmsStockAnalysisService gmsStockAnalysisService;

    @Operation(summary = "7.5 & 7.6 进销存汇总与盈亏审计")
    @GetMapping("/report")
    public List<StockAnalysisReportVO> getStockAnalysisReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword) {
        return gmsStockAnalysisService.getStockAnalysisReport(startDate, endDate, keyword);
    }

    @Operation(summary = "导出进销存财报 Excel")
    @GetMapping("/export")
    public void exportStockAnalysisReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response) throws IOException {

        // 复用底层核算逻辑，拿到报表数据
        List<StockAnalysisReportVO> list = gmsStockAnalysisService.getStockAnalysisReport(startDate, endDate, keyword);

        // 设置响应头，输出 Excel 文件流
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("门店进销存盈亏财报", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 阿里 EasyExcel 一键写出
        EasyExcel.write(response.getOutputStream(), StockAnalysisReportVO.class)
                .sheet("进销存明细")
                .doWrite(list);
    }
}
