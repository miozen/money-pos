package com.money.feature.gms.interfaces.rest;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.SysBrandConfig;
import com.money.mapper.SysBrandConfigMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "GmsBrandConfig", description = "品牌定价策略控制口")
@RestController
@RequestMapping("/gms/brand")
@RequiredArgsConstructor
public class GmsBrandConfigController {

    private final SysBrandConfigMapper sysBrandConfigMapper;

    // 🌟 查询策略
    @GetMapping("/config")
    public Map<String, Object> getConfig(@RequestParam("brandId") String brandId) {
        SysBrandConfig config = sysBrandConfigMapper.selectOne(
                new LambdaQueryWrapper<SysBrandConfig>().eq(SysBrandConfig::getBrand, brandId)
        );

        Map<String, Object> result = new HashMap<>();

        if (config != null) {
            result.put("couponEnabled", config.getCouponEnabled());
            if (StrUtil.isNotBlank(config.getLevelCodes())) {
                result.put("levelCodes", config.getLevelCodes().split(","));
            } else {
                result.put("levelCodes", new String[0]);
            }
        } else {
            result.put("couponEnabled", true);
            result.put("levelCodes", null);
        }

        return result;
    }

    // 🌟 保存策略
    @PostMapping("/config")
    public void saveConfig(@RequestBody SysBrandConfig dto) {
        // 先查查数据库里有没有这条品牌的配置记录
        SysBrandConfig exist = sysBrandConfigMapper.selectOne(
                new LambdaQueryWrapper<SysBrandConfig>().eq(SysBrandConfig::getBrand, dto.getBrand())
        );

        if (exist != null) {
            // 如果有，就更新它的策略和等级
            exist.setCouponEnabled(dto.getCouponEnabled());
            exist.setLevelCodes(dto.getLevelCodes());
            sysBrandConfigMapper.updateById(exist);
        } else {
            // 如果没有，就直接插入一条新纪录
            sysBrandConfigMapper.insert(dto);
        }
    }
}
