package com.money.feature.trade.application.orderquery;

import com.money.service.SysDictDetailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class OrderStatusDictionaryIntegrationTest {

    @Autowired
    private SysDictDetailService sysDictDetailService;

    @Test
    void flywayKeepsTheLegacyReturnStatusAndSeedsTheCanonicalRefundedStatus() {
        Map<String, String> statuses = sysDictDetailService.getValueToCnDescMap("orderStatus");

        assertThat(statuses)
                .containsEntry("PAID", "已支付")
                .containsEntry("PARTIAL_REFUNDED", "部分退货")
                .containsEntry("RETURN", "已退单")
                .containsEntry("REFUNDED", "已退单");
    }
}
