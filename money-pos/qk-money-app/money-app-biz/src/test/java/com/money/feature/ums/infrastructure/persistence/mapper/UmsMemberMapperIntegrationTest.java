package com.money.feature.ums.infrastructure.persistence.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.mapper.UmsMemberMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberMapperIntegrationTest {

    @Autowired
    private UmsMemberMapper umsMemberMapper;

    @BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearTenant() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void mapperCrudUsesTheUmsLocalEntityAndAllMemberArchiveAndAssetFields() {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMember member = new UmsMember();
        member.setCode("AD257-" + suffix);
        member.setName("member " + suffix);
        member.setType("NORMAL");
        member.setPhone("139" + suffix);
        member.setProvince("province");
        member.setCity("city");
        member.setDistrict("district");
        member.setAddress("address");
        member.setCoupon(new BigDecimal("3.45"));
        member.setConsumeAmount(new BigDecimal("12.34"));
        member.setConsumeCoupon(new BigDecimal("1.23"));
        member.setConsumeTimes(4);
        member.setCancelTimes(1);
        member.setRemark("mapper regression");
        member.setLastVisitTime(LocalDateTime.of(2025, 1, 2, 3, 4, 5));
        member.setDeleted(false);
        member.setTenantId(0L);
        member.setLevelId(90000001L);
        member.setBalance(new BigDecimal("67.89"));

        assertThat(umsMemberMapper.insert(member)).isEqualTo(1);
        assertThat(member.getId()).isNotNull();
        assertThat(umsMemberMapper.selectById(member.getId()))
                .extracting(UmsMember::getCode, UmsMember::getName, UmsMember::getType, UmsMember::getPhone,
                        UmsMember::getProvince, UmsMember::getCity, UmsMember::getDistrict, UmsMember::getAddress,
                        UmsMember::getCoupon, UmsMember::getConsumeAmount, UmsMember::getConsumeCoupon,
                        UmsMember::getConsumeTimes, UmsMember::getCancelTimes, UmsMember::getRemark,
                        UmsMember::getLastVisitTime, UmsMember::getDeleted, UmsMember::getTenantId,
                        UmsMember::getLevelId, UmsMember::getBalance)
                .containsExactly(member.getCode(), member.getName(), "NORMAL", member.getPhone(), "province", "city",
                        "district", "address", new BigDecimal("3.45"), new BigDecimal("12.34"),
                        new BigDecimal("1.23"), 4, 1, "mapper regression", member.getLastVisitTime(), false,
                        0L, 90000001L, new BigDecimal("67.89"));

        member.setBalance(new BigDecimal("70.00"));
        member.setDeleted(true);
        assertThat(umsMemberMapper.updateById(member)).isEqualTo(1);
        assertThat(umsMemberMapper.selectById(member.getId()))
                .extracting(UmsMember::getBalance, UmsMember::getDeleted)
                .containsExactly(new BigDecimal("70.00"), true);

        assertThat(umsMemberMapper.deleteById(member.getId())).isEqualTo(1);
        assertThat(umsMemberMapper.selectById(member.getId())).isNull();
    }
}
