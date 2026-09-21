package com.money.mapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Transactional
class GmsGoodsMapperIntegrationTest {
 @Autowired private GmsGoodsMapper goodsMapper;
 @Test void mapperCrudUsesGmsLocalGoodsEntity() { String s=Long.toString(System.nanoTime(),36); GmsGoods g=new GmsGoods(); g.setBarcode("AD245-"+s); g.setName("商品"+s); g.setBrandId(1L); g.setCategoryId(1L); g.setUnit("件"); g.setPurchasePrice(new BigDecimal("2.34")); g.setAvgCostPrice(new BigDecimal("2.34")); g.setSalePrice(new BigDecimal("5.67")); g.setStock(10L); g.setSales(0L); g.setStatus("SALE"); g.setTenantId(0L); assertThat(goodsMapper.insert(g)).isEqualTo(1); assertThat(g.getId()).isNotNull(); GmsGoods p=goodsMapper.selectById(g.getId()); assertThat(p).extracting(GmsGoods::getBarcode,GmsGoods::getName,GmsGoods::getSalePrice,GmsGoods::getStock,GmsGoods::getStatus,GmsGoods::getTenantId).containsExactly(g.getBarcode(),g.getName(),new BigDecimal("5.67"),10L,"SALE",0L); g.setStock(8L); assertThat(goodsMapper.updateById(g)).isEqualTo(1); assertThat(goodsMapper.selectById(g.getId()).getStock()).isEqualTo(8L); assertThat(goodsMapper.deleteById(g.getId())).isEqualTo(1); }
}
