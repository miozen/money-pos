package com.money.feature.ums.application.member;

import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.entity.SysDictDetail;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.SysDictDetailMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberExcelTemplateServiceIntegrationTest {

    @Autowired
    private UmsMemberExcelTemplateService templateService;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private SysDictDetailMapper dictDetailMapper;

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
    void templateContainsDynamicBrandColumnLevelDropdownAndDemoRow() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String brandName = "会员模板品牌" + suffix;
        String levelName = "会员模板等级" + suffix;

        GmsBrand brand = new GmsBrand();
        brand.setName(brandName);
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);
        SysDictDetail level = new SysDictDetail();
        level.setDict("memberType");
        level.setValue("MEMBER_TEMPLATE_" + suffix.toUpperCase());
        level.setCnDesc(levelName);
        level.setSort(9999);
        dictDetailMapper.insert(level);

        MockHttpServletResponse response = new MockHttpServletResponse();
        templateService.writeTemplate(response);

        assertThat(response.getContentType()).contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-disposition")).contains(".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            XSSFSheet sheet = workbook.getSheet("会员数据填写区");
            assertThat(sheet).isNotNull();

            DataFormatter formatter = new DataFormatter();
            List<String> headers = java.util.stream.IntStream.range(0, sheet.getRow(0).getLastCellNum())
                    .mapToObj(column -> formatter.formatCellValue(sheet.getRow(0).getCell(column)))
                    .collect(java.util.stream.Collectors.toList());
            assertThat(headers).startsWith("*会员姓名(必填)", "*手机号(必填11位)", "初始会员余额(本金)", "初始会员券(赠送)", "初始满减券(张数)");
            int brandColumn = headers.indexOf("[品牌特权] " + brandName);
            assertThat(brandColumn).isGreaterThanOrEqualTo(5);
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(0))).isEqualTo("张老板");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(4))).isEqualTo("2");
            assertThat(sheet.getDataValidations()).anySatisfy(validation -> {
                assertThat(validation.getRegions().getCellRangeAddresses())
                        .anySatisfy(range -> assertThat(range.getFirstColumn()).isEqualTo(brandColumn));
            });
            assertThat(sheet.getDataValidations()).anyMatch(validation -> {
                String[] options = validation.getValidationConstraint().getExplicitListValues();
                return options != null && Arrays.asList(options).contains(levelName);
            });
        }
    }
}
