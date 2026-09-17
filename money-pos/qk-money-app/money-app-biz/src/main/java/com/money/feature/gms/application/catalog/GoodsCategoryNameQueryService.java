package com.money.feature.gms.application.catalog;

import com.money.contract.goods.GoodsCategoryNameQuery;
import com.money.entity.GmsGoodsCategory;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** GMS implementation of category ID-to-name translation for report display. */
@Service
@RequiredArgsConstructor
class GoodsCategoryNameQueryService implements GoodsCategoryNameQuery {
    private final GmsGoodsCategoryMapper categoryMapper;

    @Override
    public Map<String, String> findNamesByIds(Collection<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return Collections.emptyMap();
        Map<String, Long> numericIds = new LinkedHashMap<>();
        for (String categoryId : categoryIds) {
            Long parsed = parseId(categoryId);
            if (parsed != null) numericIds.put(categoryId.trim(), parsed);
        }
        if (numericIds.isEmpty()) return Collections.emptyMap();
        Map<Long, String> namesById = new LinkedHashMap<>();
        for (GmsGoodsCategory category : categoryMapper.selectBatchIds(numericIds.values())) {
            namesById.put(category.getId(), category.getName());
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : numericIds.entrySet()) {
            String name = namesById.get(entry.getValue());
            if (name != null) result.put(entry.getKey(), name);
        }
        return result;
    }

    private Long parseId(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try { return Long.valueOf(id.trim()); } catch (NumberFormatException ignored) { return null; }
    }
}
