package com.money.feature.ums.application.memberbenefit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.BrandPricingLevelQuery;
import com.money.contract.goods.BrandSelectionQuery;
import com.money.dto.memberbenefit.MemberBenefitTierDTO;
import com.money.feature.ums.infrastructure.persistence.entity.UmsBrandBenefitTier;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsBrandBenefitTierMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

/** UMS-owned CRUD for future benefit tiers; issued-right snapshots are never edited here. */
@Service
@RequiredArgsConstructor
public class MemberBenefitTierService {
    private final UmsBrandBenefitTierMapper mapper;
    private final BrandSelectionQuery brands;
    private final BrandPricingLevelQuery pricingLevels;

    public List<MemberBenefitTierDTO> list() {
        return mapper.selectList(new LambdaQueryWrapper<UmsBrandBenefitTier>()
                        .orderByAsc(UmsBrandBenefitTier::getBrandId)
                        .orderByAsc(UmsBrandBenefitTier::getSortNo))
                .stream().map(this::dto).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(MemberBenefitTierDTO dto) {
        validate(dto);
        UmsBrandBenefitTier row = dto.getId() == null ? new UmsBrandBenefitTier() : require(dto.getId());
        row.setBrandId(dto.getBrandId());
        row.setTierCode(dto.getTierCode());
        row.setTierName(dto.getTierName());
        row.setConfiguredAmount(dto.getConfiguredAmount());
        row.setPricingLevelCode(dto.getPricingLevelCode());
        row.setRankValue(dto.getRankValue());
        row.setEnabled(dto.getEnabled());
        row.setSortNo(dto.getSortNo());
        row.setRemark(dto.getRemark());
        if (dto.getId() == null) mapper.insert(row); else mapper.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private UmsBrandBenefitTier require(Long id) {
        UmsBrandBenefitTier row = mapper.selectById(id);
        if (row == null) throw new BaseException("权益档位不存在");
        return row;
    }

    private void validate(MemberBenefitTierDTO dto) {
        boolean brand = brands.listBrandSelections().stream().anyMatch(item -> String.valueOf(item.getId()).equals(dto.getBrandId()));
        if (!brand) throw new BaseException("品牌不存在");
        if (!pricingLevels.findEnabledLevelCodes(dto.getBrandId()).contains(dto.getPricingLevelCode())) throw new BaseException("价格档不属于该品牌定价策略");
        if (dto.getConfiguredAmount().signum() < 0 || dto.getRankValue() < 0 || dto.getSortNo() < 0) throw new BaseException("档位金额、等级和排序必须为非负数");
        assertUnique(dto, UmsBrandBenefitTier::getTierCode, dto.getTierCode(), "同品牌档位代码已存在");
        assertUnique(dto, UmsBrandBenefitTier::getRankValue, dto.getRankValue(), "同品牌等级已存在");
    }

    private <T> void assertUnique(MemberBenefitTierDTO dto,
                                  com.baomidou.mybatisplus.core.toolkit.support.SFunction<UmsBrandBenefitTier, T> field,
                                  T value, String message) {
        LambdaQueryWrapper<UmsBrandBenefitTier> query = new LambdaQueryWrapper<UmsBrandBenefitTier>()
                .eq(UmsBrandBenefitTier::getBrandId, dto.getBrandId())
                .eq(field, value);
        if (dto.getId() != null) query.ne(UmsBrandBenefitTier::getId, dto.getId());
        if (mapper.selectCount(query) > 0) throw new BaseException(message);
    }

    private MemberBenefitTierDTO dto(UmsBrandBenefitTier row) {
        MemberBenefitTierDTO dto = new MemberBenefitTierDTO();
        dto.setId(row.getId());
        dto.setBrandId(row.getBrandId());
        dto.setTierCode(row.getTierCode());
        dto.setTierName(row.getTierName());
        dto.setConfiguredAmount(row.getConfiguredAmount());
        dto.setPricingLevelCode(row.getPricingLevelCode());
        dto.setRankValue(row.getRankValue());
        dto.setEnabled(row.getEnabled());
        dto.setSortNo(row.getSortNo());
        dto.setRemark(row.getRemark());
        return dto;
    }
}
