package com.money.contract.goods;

import java.util.List;

/** Entity-free access to the level codes enabled by a GMS brand pricing policy. */
public interface BrandPricingLevelQuery {
    List<String> findEnabledLevelCodes(String brandId);
}
