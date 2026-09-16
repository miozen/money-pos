package com.money.dto.SysBrandConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 品牌定价策略的跨边界保存与读取契约。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandPricingPolicy {

    private String brand;
    private Boolean couponEnabled;
    private String levelCodes;
}
