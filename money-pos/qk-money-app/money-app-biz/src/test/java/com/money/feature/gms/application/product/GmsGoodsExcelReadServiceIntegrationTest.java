package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.GmsGoodsDTO;
import com.money.entity.GmsBrand;
import com.money.entity.GmsGoods;
import com.money.entity.GmsGoodsCategory;
import com.money.entity.SysDictDetail;
import com.money.feature.gms.application.catalog.GmsBrandService;
import com.money.feature.gms.application.catalog.GmsGoodsCategoryService;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.service.SysDictDetailService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsGoodsExcelReadServiceIntegrationTest {

    @Autowired
    private GmsGoodsExcelReadService excelReadService;
    @Autowired
    private GmsGoodsService goodsService;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private GmsGoodsCategoryMapper categoryMapper;
    @Autowired
    private GmsGoodsMapper goodsMapper;
    @Autowired
    private SysDictDetailMapper sysDictDetailMapper;

    @BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearTenant() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void templateAndExportShareDynamicHeadersAndPreserveGoodsValues() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String memberType = "EXCEL_" + suffix.toUpperCase();
        String memberName = "Excel等级" + suffix;
        SysDictDetail dict = new SysDictDetail();
        dict.setDict("memberType");
        dict.setValue(memberType);
        dict.setCnDesc(memberName);
        dict.setSort(9999);
        sysDictDetailMapper.insert(dict);

        GmsBrand brand = new GmsBrand();
        brand.setName("品牌" + suffix);
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);
        GmsGoodsCategory category = new GmsGoodsCategory();
        category.setName("分类" + suffix);
        category.setPid(0L);
        category.setGoodsCount(0);
        category.setTenantId(0L);
        categoryMapper.insert(category);

        GmsGoodsDTO goods = new GmsGoodsDTO();
        goods.setBarcode("EXCEL-" + suffix);
        goods.setName("导出商品" + suffix);
        goods.setBrandId(brand.getId());
        goods.setCategoryId(category.getId());
        goods.setUnit("件");
        goods.setSize("500ml");
        goods.setSalePrice(new BigDecimal("12.50"));
        goods.setPurchasePrice(new BigDecimal("7.25"));
        goods.setStock(9L);
        goods.setIsDiscountParticipable(1);
        goods.setIsCombo(0);
        goods.setLevelPrices(Map.of(memberType, new BigDecimal("10.80")));
        goods.setLevelCoupons(Map.of(memberType, BigDecimal.ZERO));
        goodsService.add(goods, null);
        assertThat(goodsMapper.selectOne(new LambdaQueryWrapper<GmsGoods>()
                .eq(GmsGoods::getBarcode, goods.getBarcode()))).isNotNull();

        MockHttpServletResponse templateResponse = new MockHttpServletResponse();
        excelReadService.writeTemplate(templateResponse);
        MockHttpServletResponse exportResponse = new MockHttpServletResponse();
        excelReadService.writeExport(exportResponse);

        assertThat(templateResponse.getHeader("Content-disposition")).contains(".xlsx");
        assertThat(exportResponse.getHeader("Content-disposition")).contains(".xlsx");
        try (XSSFWorkbook template = new XSSFWorkbook(new ByteArrayInputStream(templateResponse.getContentAsByteArray()));
             XSSFWorkbook exported = new XSSFWorkbook(new ByteArrayInputStream(exportResponse.getContentAsByteArray()))) {
            XSSFSheet templateSheet = template.getSheet("商品资料填写区");
            XSSFSheet exportSheet = exported.getSheet("全量商品数据");
            assertThat(templateSheet).isNotNull();
            assertThat(exportSheet).isNotNull();
            assertThat(templateSheet.getDataValidations()).hasSizeGreaterThanOrEqualTo(2);

            DataFormatter formatter = new DataFormatter();
            List<String> templateHeaders = headers(templateSheet, formatter);
            List<String> exportHeaders = headers(exportSheet, formatter);
            assertThat(templateHeaders).isEqualTo(exportHeaders);
            assertThat(templateHeaders).contains("参与满减(请选择)", "[会员特价] " + memberName);
            assertThat(formatter.formatCellValue(templateSheet.getRow(1).getCell(5))).isEqualTo("允许");

            int memberPriceColumn = templateHeaders.indexOf("[会员特价] " + memberName);
            int exportRow = findRow(exportSheet, goods.getBarcode(), formatter);
            assertThat(exportRow).isGreaterThan(0);
            assertThat(formatter.formatCellValue(exportSheet.getRow(exportRow).getCell(2))).isEqualTo(category.getName());
            assertThat(formatter.formatCellValue(exportSheet.getRow(exportRow).getCell(3))).isEqualTo(brand.getName());
            assertThat(formatter.formatCellValue(exportSheet.getRow(exportRow).getCell(4))).isEqualTo("上架 (SALE)");
            assertThat(formatter.formatCellValue(exportSheet.getRow(exportRow).getCell(5))).isEqualTo("允许");
            assertThat(formatter.formatCellValue(exportSheet.getRow(exportRow).getCell(memberPriceColumn))).isEqualTo("10.80");
        }
    }

    @Test
    void exportSkipsPriceMatrixWhenThereAreNoGoods() throws Exception {
        GmsGoodsCategoryService categoryService = mock(GmsGoodsCategoryService.class);
        GmsBrandService brandService = mock(GmsBrandService.class);
        SysDictDetailService dictDetailService = mock(SysDictDetailService.class);
        GmsGoodsService emptyGoodsService = mock(GmsGoodsService.class);
        GmsGoodsPriceService priceService = mock(GmsGoodsPriceService.class);
        when(categoryService.list()).thenReturn(List.of());
        when(brandService.list()).thenReturn(List.of());
        when(dictDetailService.getValueToCnDescMap("memberType")).thenReturn(Map.of());
        when(emptyGoodsService.list()).thenReturn(List.of());

        GmsGoodsExcelReadService emptyExportService = new GmsGoodsExcelReadService(
                categoryService, brandService, dictDetailService, emptyGoodsService, priceService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        emptyExportService.writeExport(response);

        verifyNoInteractions(priceService);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            XSSFSheet sheet = workbook.getSheet("全量商品数据");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isZero();
        }
    }

    private List<String> headers(XSSFSheet sheet, DataFormatter formatter) {
        return java.util.stream.IntStream.range(0, sheet.getRow(0).getLastCellNum())
                .mapToObj(index -> formatter.formatCellValue(sheet.getRow(0).getCell(index)))
                .collect(java.util.stream.Collectors.toList());
    }

    private int findRow(XSSFSheet sheet, String barcode, DataFormatter formatter) {
        for (int row = 1; row <= sheet.getLastRowNum(); row++) {
            if (barcode.equals(formatter.formatCellValue(sheet.getRow(row).getCell(0)))) {
                return row;
            }
        }
        return -1;
    }
}
