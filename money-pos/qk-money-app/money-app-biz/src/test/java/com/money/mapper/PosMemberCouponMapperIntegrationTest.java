package com.money.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class PosMemberCouponMapperIntegrationTest {

    @Autowired private PosMemberCouponMapper memberCouponMapper;

    @Test
    void mapperCrudUsesUmsLocalMemberCouponEntity() {
        PosMemberCoupon coupon = new PosMemberCoupon();
        coupon.setMemberId(91001L);
        coupon.setRuleId(92001L);
        coupon.setStatus("UNUSED");
        coupon.setOrderNo("ORDER-" + System.nanoTime());
        coupon.setGetTime(LocalDateTime.now().withNano(0));
        coupon.setTenantId("0");
        assertThat(memberCouponMapper.insert(coupon)).isEqualTo(1);
        assertThat(coupon.getId()).isNotNull();

        PosMemberCoupon persisted = memberCouponMapper.selectById(coupon.getId());
        assertThat(persisted).extracting(PosMemberCoupon::getMemberId, PosMemberCoupon::getRuleId,
                        PosMemberCoupon::getStatus, PosMemberCoupon::getOrderNo, PosMemberCoupon::getTenantId)
                .containsExactly(91001L, 92001L, "UNUSED", coupon.getOrderNo(), "0");

        coupon.setStatus("USED");
        coupon.setUseTime(LocalDateTime.now().withNano(0));
        assertThat(memberCouponMapper.updateById(coupon)).isEqualTo(1);
        assertThat(memberCouponMapper.selectById(coupon.getId()).getStatus()).isEqualTo("USED");
        assertThat(memberCouponMapper.deleteById(coupon.getId())).isEqualTo(1);
    }
}
