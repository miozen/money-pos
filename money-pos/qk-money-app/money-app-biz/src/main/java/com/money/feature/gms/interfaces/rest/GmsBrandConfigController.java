package com.money.feature.gms.interfaces.rest;

import com.money.dto.SysBrandConfig.BrandPricingPolicy;
import com.money.dto.SysBrandConfig.BrandPricingPolicyView;
import com.money.feature.gms.application.config.GmsBrandPricingConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "GmsBrandConfig", description = "品牌定价策略控制口")
@RestController
@RequestMapping("/gms/brand")
@RequiredArgsConstructor
public class GmsBrandConfigController {

    private final GmsBrandPricingConfigService gmsBrandPricingConfigService;

    // 🌟 查询策略
    @GetMapping("/config")
    public BrandPricingPolicyView getConfig(@RequestParam("brandId") String brandId) {
        return gmsBrandPricingConfigService.getBrandPricingPolicy(brandId);
    }

    // 🌟 保存策略
    @PostMapping("/config")
    public void saveConfig(@RequestBody BrandPricingPolicy policy) {
        gmsBrandPricingConfigService.saveBrandPricingPolicy(policy);
    }
}
