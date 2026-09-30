package com.money.feature.ums.interfaces.rest;

import com.money.feature.trade.interfaces.rest.MemberBenefitTradeHistoryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the ME-1.5 read endpoints permission-protected while leaving POS write routes untouched. */
class UmsMemberBenefitRouteContractTest {
    @Test
    void benefitOverviewLogsAndTradeHistoryUseMemberReadPermission() throws Exception {
        assertRoute(UmsMemberBenefitController.class, "overview", "/overview");
        assertRoute(UmsMemberBenefitController.class, "targetLogs", "/target-logs");
        assertRoute(MemberBenefitTradeHistoryController.class, "list");
    }

    private void assertRoute(Class<?> controller, String methodName, String... paths) throws Exception {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(paths);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("@rbac.hasPermission('umsMember:list')");
    }
}
