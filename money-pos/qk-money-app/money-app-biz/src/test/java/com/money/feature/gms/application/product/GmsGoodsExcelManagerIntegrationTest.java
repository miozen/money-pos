package com.money.feature.gms.application.product;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCategory;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import com.money.feature.sys.infrastructure.persistence.entity.SysBrandConfig;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.mapper.SysBrandConfigMapper;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
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
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsGoodsExcelManagerIntegrationTest {

    @Autowired private GmsGoodsExcelManager excelManager;
    @Autowired private GmsBrandMapper brandMapper;
    @Autowired private GmsGoodsCategoryMapper categoryMapper;
    @Autowired private GmsGoodsMapper goodsMapper;
    @Autowired private PosSkuLevelPriceMapper levelPriceMapper;
    @Autowired private SysBrandConfigMapper brandConfigMapper;

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
    void importUsesSysCouponPolicyForEnabledAndDisabledBrands() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoodsCategory category = category("分类" + suffix);
        GmsBrand enabled = brand("开启品牌" + suffix);
        GmsBrand disabled = brand("关闭品牌" + suffix);
        savePolicy(enabled.getId(), true);
        savePolicy(disabled.getId(), false);

        excelManager.importGoods(workbook(suffix, category.getName(), enabled.getName(), disabled.getName()));

        assertCoupon("ENABLED-" + suffix, new BigDecimal("1.50"));
        assertCoupon("DISABLED-" + suffix, BigDecimal.ZERO);
    }

    private GmsGoodsCategory category(String name) {
        GmsGoodsCategory category = new GmsGoodsCategory();
        category.setName(name); category.setPid(0L); category.setGoodsCount(0); category.setTenantId(0L);
        categoryMapper.insert(category);
        return category;
    }

    private GmsBrand brand(String name) {
        GmsBrand brand = new GmsBrand();
        brand.setName(name); brand.setGoodsCount(0); brand.setTenantId(0L);
        brandMapper.insert(brand);
        return brand;
    }

    private void savePolicy(Long brandId, boolean enabled) {
        SysBrandConfig policy = new SysBrandConfig();
        policy.setBrand(String.valueOf(brandId)); policy.setCouponEnabled(enabled); policy.setTenantId(0L);
        brandConfigMapper.insert(policy);
    }

    private MockMultipartFile workbook(String suffix, String category, String enabledBrand, String disabledBrand) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<List<String>> rows = Arrays.asList(
                Arrays.asList("商品条码", "商品名称", "所属分类", "所属品牌", "零售价", "普通会员价"),
                Arrays.asList("ENABLED-" + suffix, "启用商品" + suffix, category, enabledBrand, "10.00", "8.50"),
                Arrays.asList("DISABLED-" + suffix, "关闭商品" + suffix, category, disabledBrand, "10.00", "8.50"));
        EasyExcel.write(output).sheet("商品").doWrite(rows);
        return new MockMultipartFile("file", "goods.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }

    private void assertCoupon(String barcode, BigDecimal coupon) {
        GmsGoods goods = goodsMapper.selectOne(new LambdaQueryWrapper<GmsGoods>().eq(GmsGoods::getBarcode, barcode));
        assertThat(goods).isNotNull();
        PosSkuLevelPrice price = levelPriceMapper.selectOne(new LambdaQueryWrapper<PosSkuLevelPrice>()
                .eq(PosSkuLevelPrice::getSkuId, goods.getId()).eq(PosSkuLevelPrice::getLevelId, "VIP"));
        assertThat(price).isNotNull();
        assertThat(price.getMemberCoupon()).isEqualByComparingTo(coupon);
    }
}
