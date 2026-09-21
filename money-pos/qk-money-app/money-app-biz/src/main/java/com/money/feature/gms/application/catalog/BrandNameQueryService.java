package com.money.feature.gms.application.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.BrandNameQuery;
import com.money.contract.goods.BrandSelectionQuery;
import com.money.contract.goods.BrandSelectionSnapshot;
import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.mapper.GmsBrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GMS implementation of brand ID to name translation for external display. */
@Service
@RequiredArgsConstructor
class BrandNameQueryService implements BrandNameQuery, BrandSelectionQuery {

    private final GmsBrandMapper brandMapper;

    @Override
    public Map<String, String> findNamesByIds(Collection<String> brandIds) {
        if (brandIds == null || brandIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> numericIds = new LinkedHashMap<>();
        for (String brandId : brandIds) {
            if (brandId == null || brandId.trim().isEmpty()) {
                continue;
            }
            Long numericId = parseId(brandId);
            if (numericId != null) {
                numericIds.putIfAbsent(brandId.trim(), numericId);
            }
        }
        if (numericIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> namesById = new LinkedHashMap<>();
        for (GmsBrand brand : brandMapper.selectBatchIds(numericIds.values())) {
            namesById.put(brand.getId(), brand.getName());
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : numericIds.entrySet()) {
            String name = namesById.get(entry.getValue());
            if (name != null) {
                result.put(entry.getKey(), name);
            }
        }
        return result;
    }

    @Override
    public List<BrandSelectionSnapshot> listBrandSelections() {
        return brandMapper.selectList(new LambdaQueryWrapper<GmsBrand>().select(GmsBrand::getId, GmsBrand::getName)).stream()
                .map(brand -> new BrandSelectionSnapshot(brand.getId(), brand.getName()))
                .collect(java.util.stream.Collectors.toList());
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
