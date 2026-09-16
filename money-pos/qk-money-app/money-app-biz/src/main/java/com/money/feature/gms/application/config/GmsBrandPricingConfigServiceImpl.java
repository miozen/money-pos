package com.money.feature.gms.application.config;

import cn.hutool.core.util.StrUtil;
import com.money.dto.SysBrandConfig.BrandPricingPolicy;
import com.money.dto.SysBrandConfig.BrandPricingPolicyView;
import com.money.service.SysBrandConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmsBrandPricingConfigServiceImpl implements GmsBrandPricingConfigService {

    private final SysBrandConfigService sysBrandConfigService;

    @Override
    public BrandPricingPolicyView getBrandPricingPolicy(String brandId) {
        BrandPricingPolicy policy = sysBrandConfigService.findByBrand(brandId);
        if (policy == null) {
            return new BrandPricingPolicyView(true, null);
        }
        String[] levelCodes = StrUtil.isNotBlank(policy.getLevelCodes())
                ? policy.getLevelCodes().split(",")
                : new String[0];
        return new BrandPricingPolicyView(policy.getCouponEnabled(), levelCodes);
    }

    @Override
    public void saveBrandPricingPolicy(BrandPricingPolicy policy) {
        sysBrandConfigService.save(policy);
    }
}
