package com.money.feature.gms.application.config;

import com.money.dto.SysBrandConfig.BrandPricingPolicy;
import com.money.dto.SysBrandConfig.BrandPricingPolicyView;

/**
 * GMS 品牌价格/会员券策略的路由应用边界。
 */
public interface GmsBrandPricingConfigService {

    BrandPricingPolicyView getBrandPricingPolicy(String brandId);

    void saveBrandPricingPolicy(BrandPricingPolicy policy);
}
