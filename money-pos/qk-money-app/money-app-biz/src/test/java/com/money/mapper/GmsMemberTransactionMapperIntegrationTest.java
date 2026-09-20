package com.money.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.GmsMemberTransaction;
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
class GmsMemberTransactionMapperIntegrationTest {

    @Autowired
    private GmsMemberTransactionMapper memberTransactionMapper;

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
    void mapperCrudUsesTheUmsLocalEntityWithImplicitTableAndMemberFundSnapshotFields() {
        String suffix = Long.toString(System.nanoTime(), 36);
        LocalDateTime createTime = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        GmsMemberTransaction transaction = new GmsMemberTransaction();
        transaction.setMemberId(90000001L);
        transaction.setType("RECHARGE");
        transaction.setAmount(new BigDecimal("12.34"));
        transaction.setBalanceAfter(new BigDecimal("56.78"));
        transaction.setOrderNo("AD213-" + suffix);
        transaction.setRemark("member fund snapshot " + suffix);
        transaction.setCreateTime(createTime);
        transaction.setTenantId(0L);

        assertThat(memberTransactionMapper.insert(transaction)).isEqualTo(1);
        assertThat(transaction.getId()).isNotNull();
        assertThat(memberTransactionMapper.selectById(transaction.getId()))
                .extracting(GmsMemberTransaction::getMemberId,
                        GmsMemberTransaction::getType,
                        GmsMemberTransaction::getAmount,
                        GmsMemberTransaction::getBalanceAfter,
                        GmsMemberTransaction::getOrderNo,
                        GmsMemberTransaction::getRemark,
                        GmsMemberTransaction::getCreateTime,
                        GmsMemberTransaction::getTenantId)
                .containsExactly(90000001L, "RECHARGE", new BigDecimal("12.34"), new BigDecimal("56.78"),
                        transaction.getOrderNo(), transaction.getRemark(), createTime, 0L);

        transaction.setAmount(new BigDecimal("-3.21"));
        transaction.setBalanceAfter(new BigDecimal("53.57"));
        transaction.setRemark("updated " + suffix);
        assertThat(memberTransactionMapper.updateById(transaction)).isEqualTo(1);
        assertThat(memberTransactionMapper.selectById(transaction.getId()))
                .extracting(GmsMemberTransaction::getAmount,
                        GmsMemberTransaction::getBalanceAfter,
                        GmsMemberTransaction::getRemark)
                .containsExactly(new BigDecimal("-3.21"), new BigDecimal("53.57"), "updated " + suffix);

        assertThat(memberTransactionMapper.deleteById(transaction.getId())).isEqualTo(1);
        assertThat(memberTransactionMapper.selectById(transaction.getId())).isNull();
    }
}
