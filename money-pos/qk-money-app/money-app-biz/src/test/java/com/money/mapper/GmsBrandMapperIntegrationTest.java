package com.money.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Transactional
class GmsBrandMapperIntegrationTest {
 @Autowired private GmsBrandMapper brandMapper;
 @BeforeEach void authenticateTenant() {
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A"));
 }
 @AfterEach void clearTenant() { SecurityContextHolder.clearContext(); }
 @Test void mapperCrudUsesGmsLocalBaseEntity() { String s=Long.toString(System.nanoTime(),36); GmsBrand b=new GmsBrand(); b.setName("AD239-"+s); b.setLogo("logo/"+s); b.setDescription("brand"); b.setGoodsCount(2); b.setTenantId(0L); assertThat(brandMapper.insert(b)).isEqualTo(1); assertThat(b.getId()).isNotNull(); GmsBrand p=brandMapper.selectById(b.getId()); assertThat(p).extracting(GmsBrand::getName,GmsBrand::getLogo,GmsBrand::getDescription,GmsBrand::getGoodsCount,GmsBrand::getTenantId).containsExactly(b.getName(),b.getLogo(),"brand",2,0L); assertThat(p.getCreateTime()).isNotNull(); b.setGoodsCount(3); assertThat(brandMapper.updateById(b)).isEqualTo(1); assertThat(brandMapper.selectById(b.getId()).getGoodsCount()).isEqualTo(3); assertThat(brandMapper.deleteById(b.getId())).isEqualTo(1); }
}
