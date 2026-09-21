package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.entity.SysDictDetail;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberImportServiceIntegrationTest {

    @Autowired
    private UmsMemberImportService importService;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private SysDictDetailMapper dictDetailMapper;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberBrandLevelMapper memberBrandLevelMapper;
    @Autowired
    private UmsMemberProfileService profileService;

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
    void importResolvesDynamicBrandColumnThroughSelectionContract() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String brandName = "导入品牌" + suffix;
        String levelName = "导入等级" + suffix;
        String levelCode = "IMPORT_" + suffix.toUpperCase();
        String phone = "136" + String.format("%08d", Math.abs(suffix.hashCode()) % 100000000);

        GmsBrand brand = new GmsBrand();
        brand.setName(brandName);
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);
        SysDictDetail level = new SysDictDetail();
        level.setDict("memberType");
        level.setValue(levelCode);
        level.setCnDesc(levelName);
        level.setSort(9999);
        dictDetailMapper.insert(level);

        MockMultipartFile workbook = new MockMultipartFile("file", "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", createWorkbook(brandName, levelName, phone));

        assertThat(importService.importMembers(workbook)).contains("成功新增 1 位");

        UmsMember member = memberMapper.selectOne(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getPhone, phone).last("LIMIT 1"));
        assertThat(member).isNotNull();
        UmsMemberBrandLevel brandLevel = memberBrandLevelMapper.selectOne(new LambdaQueryWrapper<UmsMemberBrandLevel>()
                .eq(UmsMemberBrandLevel::getMemberId, member.getId())
                .eq(UmsMemberBrandLevel::getBrand, String.valueOf(brand.getId())));
        assertThat(brandLevel).isNotNull();
        assertThat(brandLevel.getLevelCode()).isEqualTo(levelCode);
        UmsMemberQueryDTO query = new UmsMemberQueryDTO();
        query.setPhone(phone);
        assertThat(profileService.list(query).getRecords()).singleElement().satisfies(profile ->
                assertThat(profile.getBrandLevelDesc()).containsEntry(brandName, levelName));
    }

    private byte[] createWorkbook(String brandName, String levelName, String phone) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("会员数据填写区");
            String[] headers = {"*会员姓名(必填)", "*手机号(必填11位)", "初始会员余额(本金)", "初始会员券(赠送)",
                    "初始满减券(张数)", "[品牌特权] " + brandName};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                headerRow.createCell(column).setCellValue(headers[column]);
            }
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("导入会员");
            row.createCell(1).setCellValue(phone);
            row.createCell(5).setCellValue(levelName);
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
