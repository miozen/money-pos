package com.money.feature.gms.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GmsRoutePermissionContractTest {

    @Test
    void formerlyUnprotectedGmsRoutesUseExistingPermissionCodes() throws Exception {
        assertPermission(GmsGoodsExcelController.class, "importGoods", "gmsGoods:add", org.springframework.web.multipart.MultipartFile.class);
        assertPermission(GmsGoodsExcelController.class, "downloadTemplate", "gmsGoods:list", javax.servlet.http.HttpServletResponse.class);
        assertPermission(GmsGoodsExcelController.class, "exportGoods", "gmsGoods:list", javax.servlet.http.HttpServletResponse.class);
        assertPermission(GmsBrandConfigController.class, "getConfig", "gmsBrand:list", String.class);
        assertPermission(GmsBrandConfigController.class, "saveConfig", "gmsBrand:edit", com.money.dto.SysBrandConfig.BrandPricingPolicy.class);
        assertPermission(GmsTurnoverController.class, "getTurnoverWarnings", "gmsGoods:list");
        assertPermission(GmsTurnoverController.class, "getWarningTrend", "gmsGoods:list");
        assertPermission(GmsTurnoverController.class, "exportReplenish", "gmsGoods:list", javax.servlet.http.HttpServletResponse.class);
        assertPermission(GmsTurnoverController.class, "exportDeadStock", "gmsGoods:list", javax.servlet.http.HttpServletResponse.class);
        assertPermission(GmsStockAnalysisController.class, "getStockAnalysisReport", "gmsGoods:list", String.class, String.class, String.class);
        assertPermission(GmsStockAnalysisController.class, "exportStockAnalysisReport", "gmsGoods:list", String.class, String.class, String.class, javax.servlet.http.HttpServletResponse.class);
    }

    private void assertPermission(Class<?> type, String methodName, String permission, Class<?>... parameterTypes) throws Exception {
        Method method = type.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(permission);
    }
}
