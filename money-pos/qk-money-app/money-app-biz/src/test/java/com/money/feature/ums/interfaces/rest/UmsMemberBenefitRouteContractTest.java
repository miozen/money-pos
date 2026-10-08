package com.money.feature.ums.interfaces.rest;

import com.money.feature.trade.interfaces.rest.MemberBenefitTradeHistoryController;
import com.money.feature.trade.interfaces.rest.PosController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the ME-1.5 read endpoints permission-protected while leaving POS write routes untouched. */
class UmsMemberBenefitRouteContractTest {
    @Test
    void benefitReadsUseOperateOrManageAndHighRiskWritesUseManage() throws Exception {
        assertRoute(UmsMemberBenefitController.class, "overview", "@rbac.hasPermission('memberBenefit:operate', 'memberBenefit:manage')", "/overview");
        assertRoute(UmsMemberBenefitController.class, "targetLogs", "@rbac.hasPermission('memberBenefit:operate', 'memberBenefit:manage')", "/target-logs");
        assertRoute(MemberBenefitTradeHistoryController.class, "list", "@rbac.hasPermission('memberBenefit:operate', 'memberBenefit:manage')");
        assertRoute(PosController.class, "refundAmountPickup", "@rbac.hasPermission('memberBenefit:manage')");
        assertRoute(PosController.class, "confirmTarget", "@rbac.hasPermission('memberBenefit:manage')");
        assertRoute(PosController.class, "settleDeferredQuantity", "@rbac.hasPermission('memberBenefit:operate')");
        assertRoute(PosController.class, "settleTarget", "@rbac.hasPermission('memberBenefit:operate')");
    }

    private void assertRoute(Class<?> controller, String methodName, String permission, String... paths) throws Exception {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        if (paths.length > 0) assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(paths);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(permission);
    }
}
