package com.money.service;

import com.money.dto.SysBrandConfig.BrandPricingPolicy;

/**
 * SYS 品牌定价策略的持久化边界。
 */
public interface SysBrandConfigService {

    BrandPricingPolicy findByBrand(String brand);

    void save(BrandPricingPolicy policy);
}
