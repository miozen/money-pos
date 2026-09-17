package com.money.feature.ums.application.member;

import com.money.contract.goods.BrandNameQuery;
import com.money.contract.member.HomeMemberDistributionQuery;
import com.money.contract.member.HomeMemberDistributionSnapshot;
import com.money.mapper.UmsMemberBrandLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** UMS implementation of the active member-level distribution used by HOME. */
@Service
@RequiredArgsConstructor
class HomeMemberDistributionQueryService implements HomeMemberDistributionQuery {

    private final UmsMemberBrandLevelMapper memberBrandLevelMapper;
    private final BrandNameQuery brandNameQuery;

    @Override
    public List<HomeMemberDistributionSnapshot> listActiveMemberDistribution() {
        List<Map<String, Object>> rows = memberBrandLevelMapper.getHomeMemberDistributionData();
        Set<String> brandIds = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String brandId = valueAsString(row.get("brandId"));
            if (brandId != null) {
                brandIds.add(brandId);
            }
        }
        Map<String, String> namesById = brandNameQuery.findNamesByIds(brandIds);
        List<HomeMemberDistributionSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String brandId = valueAsString(row.get("brandId"));
            String brandName = namesById.get(brandId);
            result.add(new HomeMemberDistributionSnapshot(
                    brandName != null ? brandName : brandId,
                    valueAsString(row.get("levelCode")),
                    valueAsInt(row.get("memberCount"))));
        }
        return result;
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }

    private int valueAsInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
