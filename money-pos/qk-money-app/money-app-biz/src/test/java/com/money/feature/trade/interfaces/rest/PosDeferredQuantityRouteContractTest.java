package com.money.feature.trade.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the public route and cashier-permission contract without changing normal POS endpoints. */
class PosDeferredQuantityRouteContractTest {
    @Test
    void deferredQuantityEndpointsAndNormalSettlementRemainCashierProtected() throws Exception {
        assertRoute("settleAccounts", "/settleAccounts");
        assertRoute("settleDeferredQuantity", "/deferred-quantity/settle");
        assertRoute("pickupDeferredQuantity", "/deferred-quantity/pickup");
        assertRoute("purchaseAmountPackage", "/amount-package/purchase");
        assertRoute("pickupAmountPackage", "/amount-package/pickup");
        assertRoute("refundAmountPickup", "/amount-package/pickup-refund");
        assertRoute("settleTarget", "/target/settle");
        assertRoute("supplementTarget", "/target/supplement");
        assertRoute("waiveTarget", "/target/waive");
        assertRoute("confirmTarget", "/target/confirm");
    }

    private void assertRoute(String methodName, String path) throws Exception {
        Method method = java.util.Arrays.stream(PosController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(path);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("@rbac.hasPermission('pos:cashier')");
    }
}
