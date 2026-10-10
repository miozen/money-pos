package com.money.feature.trade.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the public route and frozen benefit-capability contract without changing normal POS settlement. */
class PosDeferredQuantityRouteContractTest {
    @Test
    void benefitRoutesUseFrozenCapabilitiesWhileNormalSettlementRemainsCashierProtected() throws Exception {
        assertRoute("settleAccounts", "/settleAccounts", "pos:cashier");
        assertRoute("settleDeferredQuantity", "/deferred-quantity/settle", "memberBenefit:operate");
        assertRoute("trialDeferredQuantity", "/deferred-quantity/trial", "memberBenefit:operate");
        assertRoute("pickupDeferredQuantity", "/deferred-quantity/pickup", "memberBenefit:operate");
        assertRoute("previewDeferredQuantityPickup", "/deferred-quantity/pickup-preview", "memberBenefit:operate");
        assertRoute("printDeferredQuantityPickupReceipt", "/deferred-quantity/pickup-receipt", "memberBenefit:operate");
        assertReadRoute("previewDeferredQuantityPickupReceipt", "/deferred-quantity/pickup-receipt", "memberBenefit:operate");
        assertRoute("purchaseAmountPackage", "/amount-package/purchase", "memberBenefit:operate");
        assertRoute("pickupAmountPackage", "/amount-package/pickup", "memberBenefit:operate");
        assertRoute("refundAmountPickup", "/amount-package/pickup-refund", "memberBenefit:manage");
        assertReadRoute("previewAmountPickupReceipt", "/amount-package/pickup-receipt", "memberBenefit:operate");
        assertRoute("settleTarget", "/target/settle", "memberBenefit:operate");
        assertRoute("supplementTarget", "/target/supplement", "memberBenefit:operate");
        assertRoute("waiveTarget", "/target/waive", "memberBenefit:operate");
        assertRoute("confirmTarget", "/target/confirm", "memberBenefit:manage");
    }

    private void assertRoute(String methodName, String path, String permission) throws Exception {
        Method method = java.util.Arrays.stream(PosController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(path);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("@rbac.hasPermission('" + permission + "')");
    }

    private void assertReadRoute(String methodName, String path, String permission) throws Exception {
        Method method = java.util.Arrays.stream(PosController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(path);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("@rbac.hasPermission('" + permission + "')");
    }
}
