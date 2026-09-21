package com.money.contract.system;

import java.util.Collection;
import java.util.Map;

/** SYS-owned brand coupon settings required by POS and GMS product import. */
public interface BrandCouponPolicyQuery {

    Map<String, Boolean> findCouponEnabledByBrands(Collection<String> brandIds);

    Map<String, Boolean> findAllCouponEnabledByBrand();
}
