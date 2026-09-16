package com.money.dto.SysBrandConfig;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * GMS 品牌策略读取接口的前端兼容视图。
 */
@Data
@AllArgsConstructor
public class BrandPricingPolicyView {

    private Boolean couponEnabled;
    private String[] levelCodes;
}
