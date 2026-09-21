package com.money.feature.ums.application.member;

import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.entity.SysDictDetail;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.contract.member.MemberCouponCountQuery;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberAssetExcelExportServiceIntegrationTest {

    @Autowired
    private UmsMemberAssetExcelExportService exportService;
    @Autowired
    private MemberCouponCountQuery memberCouponCountQuery;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private SysDictDetailMapper dictDetailMapper;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberBrandLevelMapper memberBrandLevelMapper;
    @Autowired
    private PosMemberCouponMapper memberCouponMapper;

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
    void exportUsesTradeCouponAggregateAndPreservesMemberAssetsAndBrandLevel() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String levelCode = "EXPORT_" + suffix.toUpperCase();
        String levelName = "导出等级" + suffix;

        GmsBrand brand = new GmsBrand();
        brand.setName("导出品牌" + suffix);
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);
        SysDictDetail level = new SysDictDetail();
        level.setDict("memberType");
        level.setValue(levelCode);
        level.setCnDesc(levelName);
        level.setSort(9999);
        dictDetailMapper.insert(level);

        UmsMember member = new UmsMember();
        member.setCode("EXPORT-" + suffix);
        member.setName("导出会员" + suffix);
        member.setType("MEMBER");
        member.setPhone("139" + suffix);
        member.setCoupon(new BigDecimal("50.00"));
        member.setBalance(new BigDecimal("500.00"));
        member.setConsumeAmount(BigDecimal.ZERO);
        member.setConsumeCoupon(BigDecimal.ZERO);
        member.setConsumeTimes(0);
        member.setCancelTimes(0);
        member.setDeleted(false);
        member.setTenantId(0L);
        memberMapper.insert(member);

        UmsMemberBrandLevel brandLevel = new UmsMemberBrandLevel();
        brandLevel.setMemberId(member.getId());
        brandLevel.setBrand(String.valueOf(brand.getId()));
        brandLevel.setLevelCode(levelCode);
        brandLevel.setTenantId(0L);
        memberBrandLevelMapper.insert(brandLevel);
        insertCoupon(member.getId(), "UNUSED");
        insertCoupon(member.getId(), "USED");

        assertThat(memberCouponCountQuery.countUnusedCouponsByMemberIds(List.of(member.getId())))
                .containsEntry(member.getId(), 1L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        exportService.writeExport(response);

        assertThat(response.getHeader("Content-disposition")).contains(".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            XSSFSheet sheet = workbook.getSheet("老会员数据");
            assertThat(sheet).isNotNull();
            DataFormatter formatter = new DataFormatter();
            int rowIndex = findMemberRow(sheet, member.getPhone(), formatter);
            assertThat(rowIndex).isGreaterThan(0);
            assertThat(formatter.formatCellValue(sheet.getRow(rowIndex).getCell(0))).isEqualTo(member.getName());
            assertThat(formatter.formatCellValue(sheet.getRow(rowIndex).getCell(2))).isEqualTo("500.00");
            assertThat(formatter.formatCellValue(sheet.getRow(rowIndex).getCell(3))).isEqualTo("50.00");
            assertThat(formatter.formatCellValue(sheet.getRow(rowIndex).getCell(4))).isEqualTo("1");
            int brandColumn = findHeaderColumn(sheet, "[品牌特权] " + brand.getName(), formatter);
            assertThat(brandColumn).isGreaterThanOrEqualTo(5);
            assertThat(formatter.formatCellValue(sheet.getRow(rowIndex).getCell(brandColumn))).isEqualTo(levelName);
        }
    }

    private void insertCoupon(Long memberId, String status) {
        PosMemberCoupon coupon = new PosMemberCoupon();
        coupon.setMemberId(memberId);
        coupon.setRuleId(1L);
        coupon.setStatus(status);
        coupon.setTenantId("0");
        memberCouponMapper.insert(coupon);
    }

    private int findMemberRow(XSSFSheet sheet, String phone, DataFormatter formatter) {
        for (int row = 1; row <= sheet.getLastRowNum(); row++) {
            if (phone.equals(formatter.formatCellValue(sheet.getRow(row).getCell(1)))) {
                return row;
            }
        }
        return -1;
    }

    private int findHeaderColumn(XSSFSheet sheet, String expected, DataFormatter formatter) {
        for (int column = 0; column < sheet.getRow(0).getLastCellNum(); column++) {
            if (expected.equals(formatter.formatCellValue(sheet.getRow(0).getCell(column)))) {
                return column;
            }
        }
        return -1;
    }
}
