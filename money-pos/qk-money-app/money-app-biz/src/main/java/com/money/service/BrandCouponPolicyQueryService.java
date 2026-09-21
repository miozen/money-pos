package com.money.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.system.BrandCouponPolicyQuery;
import com.money.feature.sys.infrastructure.persistence.entity.SysBrandConfig;
import com.money.mapper.SysBrandConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class BrandCouponPolicyQueryService implements BrandCouponPolicyQuery {

    private final SysBrandConfigMapper sysBrandConfigMapper;

    @Override
    public Map<String, Boolean> findCouponEnabledByBrands(Collection<String> brandIds) {
        if (brandIds == null || brandIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return toCouponEnabledMap(sysBrandConfigMapper.selectList(
                new LambdaQueryWrapper<SysBrandConfig>().in(SysBrandConfig::getBrand, brandIds)));
    }

    @Override
    public Map<String, Boolean> findAllCouponEnabledByBrand() {
        return toCouponEnabledMap(sysBrandConfigMapper.selectList(new LambdaQueryWrapper<SysBrandConfig>()));
    }

    private Map<String, Boolean> toCouponEnabledMap(List<SysBrandConfig> configs) {
        Map<String, Boolean> policies = new LinkedHashMap<>();
        if (configs != null) {
            for (SysBrandConfig config : configs) {
                if (config.getBrand() != null && config.getCouponEnabled() != null) {
                    policies.put(config.getBrand(), config.getCouponEnabled());
                }
            }
        }
        return Collections.unmodifiableMap(policies);
    }
}
