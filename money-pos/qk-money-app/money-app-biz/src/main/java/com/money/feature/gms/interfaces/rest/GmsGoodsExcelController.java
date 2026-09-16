package com.money.feature.gms.interfaces.rest;

import com.money.feature.gms.application.product.GmsGoodsExcelManager;
import com.money.feature.gms.application.product.GmsGoodsExcelReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Tag(name = "gmsGoodsExcel", description = "商品导入导出中心")
@RestController
@RequiredArgsConstructor
public class GmsGoodsExcelController {

    private final GmsGoodsExcelManager gmsGoodsExcelManager;
    private final GmsGoodsExcelReadService gmsGoodsExcelReadService;

    @Operation(summary = "Excel智能矩阵批量导入商品")
    @PostMapping("/gms/goods/import")
    public String importGoods(@RequestParam("file") MultipartFile file) {
        return gmsGoodsExcelManager.importGoods(file);
    }

    @GetMapping("/gms/goods/template")
    @Operation(summary = "下载智能商品导入模板")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        gmsGoodsExcelReadService.writeTemplate(response);
    }

    @GetMapping("/gms/goods/export")
    @Operation(summary = "导出全量商品资料到 Excel (包含完整价格矩阵)")
    public void exportGoods(HttpServletResponse response) throws IOException {
        gmsGoodsExcelReadService.writeExport(response);
    }
}
