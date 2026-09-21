package com.money.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsStockLogMapperIntegrationTest {
    @Autowired private GmsStockLogMapper stockLogMapper;

    @Test void mapperCrudUsesGmsLocalInventoryLedgerEntity() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(90000001L); log.setGoodsName("库存测试"); log.setGoodsBarcode("AD241-" + suffix);
        log.setType("INBOUND"); log.setQuantity(3); log.setAfterQuantity(10);
        log.setCostPriceSnapshot(new BigDecimal("2.34")); log.setImpactAmount(new BigDecimal("7.02"));
        log.setOrderNo("AD241-" + suffix); log.setRemark("mapper regression"); log.setCreator("test"); log.setTenantId(0L);
        assertThat(stockLogMapper.insert(log)).isEqualTo(1); assertThat(log.getId()).isNotNull();
        GmsStockLog persisted = stockLogMapper.selectById(log.getId());
        assertThat(persisted).extracting(GmsStockLog::getGoodsId, GmsStockLog::getGoodsBarcode,
                GmsStockLog::getType, GmsStockLog::getQuantity, GmsStockLog::getAfterQuantity,
                GmsStockLog::getCostPriceSnapshot, GmsStockLog::getImpactAmount, GmsStockLog::getTenantId)
                .containsExactly(90000001L, log.getGoodsBarcode(), "INBOUND", 3, 10,
                        new BigDecimal("2.34"), new BigDecimal("7.02"), 0L);
        log.setAfterQuantity(12); log.setImpactAmount(new BigDecimal("4.68"));
        assertThat(stockLogMapper.updateById(log)).isEqualTo(1);
        assertThat(stockLogMapper.selectById(log.getId()).getAfterQuantity()).isEqualTo(12);
        assertThat(stockLogMapper.deleteById(log.getId())).isEqualTo(1);
    }
}
