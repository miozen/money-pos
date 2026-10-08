package com.money.feature.ums.application.memberbenefit;

import com.money.contract.goods.BrandPricingLevelQuery;
import com.money.contract.goods.BrandSelectionQuery;
import com.money.contract.goods.BrandSelectionSnapshot;
import com.money.dto.memberbenefit.MemberBenefitTierDTO;
import com.money.feature.ums.infrastructure.persistence.entity.UmsBrandBenefitTier;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsBrandBenefitTierMapper;
import com.money.web.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MemberBenefitTierServiceTest {

    @Test
    void savesOnlyFrozenTierFieldsAfterValidatingExistingGmsBrandAndLevel() {
        UmsBrandBenefitTierMapper mapper = mock(UmsBrandBenefitTierMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        MemberBenefitTierService service = service(mapper,
                () -> List.of(new BrandSelectionSnapshot(8L, "品牌 A")),
                brandId -> List.of("VIP-A"));

        service.save(request());

        org.mockito.ArgumentCaptor<UmsBrandBenefitTier> row = org.mockito.ArgumentCaptor.forClass(UmsBrandBenefitTier.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue()).extracting(UmsBrandBenefitTier::getBrandId, UmsBrandBenefitTier::getTierCode,
                        UmsBrandBenefitTier::getTierName, UmsBrandBenefitTier::getPricingLevelCode,
                        UmsBrandBenefitTier::getRankValue, UmsBrandBenefitTier::getEnabled, UmsBrandBenefitTier::getSortNo)
                .containsExactly("8", "TIER-A", "权益 A", "VIP-A", 2, true, 3);
        assertThat(row.getValue().getConfiguredAmount()).isEqualByComparingTo("299.00");
    }

    @Test
    void rejectsUnknownBrandOrPriceLevelOutsideThatBrandPolicy() {
        UmsBrandBenefitTierMapper mapper = mock(UmsBrandBenefitTierMapper.class);
        MemberBenefitTierService unknownBrand = service(mapper, List::of, brandId -> List.of("VIP-A"));
        assertThatThrownBy(() -> unknownBrand.save(request())).isInstanceOf(BaseException.class).hasMessageContaining("品牌不存在");

        MemberBenefitTierService invalidLevel = service(mapper,
                () -> List.of(new BrandSelectionSnapshot(8L, "品牌 A")), brandId -> List.of("VIP-B"));
        assertThatThrownBy(() -> invalidLevel.save(request())).isInstanceOf(BaseException.class).hasMessageContaining("价格档");
    }

    @Test
    void rejectsDuplicateCodeAndRankBeforeDatabaseConstraint() {
        UmsBrandBenefitTierMapper mapper = mock(UmsBrandBenefitTierMapper.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        MemberBenefitTierService service = service(mapper,
                () -> List.of(new BrandSelectionSnapshot(8L, "品牌 A")), brandId -> List.of("VIP-A"));

        assertThatThrownBy(() -> service.save(request())).isInstanceOf(BaseException.class).hasMessageContaining("档位代码");
    }

    private MemberBenefitTierService service(UmsBrandBenefitTierMapper mapper, BrandSelectionQuery brands,
                                             BrandPricingLevelQuery levels) {
        return new MemberBenefitTierService(mapper, brands, levels);
    }

    private MemberBenefitTierDTO request() {
        MemberBenefitTierDTO dto = new MemberBenefitTierDTO();
        dto.setBrandId("8");
        dto.setTierCode("TIER-A");
        dto.setTierName("权益 A");
        dto.setConfiguredAmount(new BigDecimal("299.00"));
        dto.setPricingLevelCode("VIP-A");
        dto.setRankValue(2);
        dto.setEnabled(true);
        dto.setSortNo(3);
        dto.setRemark("test");
        return dto;
    }
}
