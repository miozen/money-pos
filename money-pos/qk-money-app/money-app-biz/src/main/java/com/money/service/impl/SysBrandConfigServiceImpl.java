package com.money.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.SysBrandConfig.BrandPricingPolicy;
import com.money.feature.sys.infrastructure.persistence.entity.SysBrandConfig;
import com.money.mapper.SysBrandConfigMapper;
import com.money.service.SysBrandConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysBrandConfigServiceImpl implements SysBrandConfigService {

    private final SysBrandConfigMapper sysBrandConfigMapper;

    @Override
    public BrandPricingPolicy findByBrand(String brand) {
        SysBrandConfig config = sysBrandConfigMapper.selectOne(
                new LambdaQueryWrapper<SysBrandConfig>().eq(SysBrandConfig::getBrand, brand));
        if (config == null) {
            return null;
        }
        return new BrandPricingPolicy(config.getBrand(), config.getCouponEnabled(), config.getLevelCodes());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(BrandPricingPolicy policy) {
        SysBrandConfig existing = sysBrandConfigMapper.selectOne(
                new LambdaQueryWrapper<SysBrandConfig>().eq(SysBrandConfig::getBrand, policy.getBrand()));
        if (existing != null) {
            existing.setCouponEnabled(policy.getCouponEnabled());
            existing.setLevelCodes(policy.getLevelCodes());
            sysBrandConfigMapper.updateById(existing);
            return;
        }

        SysBrandConfig config = new SysBrandConfig();
        config.setBrand(policy.getBrand());
        config.setCouponEnabled(policy.getCouponEnabled());
        config.setLevelCodes(policy.getLevelCodes());
        sysBrandConfigMapper.insert(config);
    }
}
