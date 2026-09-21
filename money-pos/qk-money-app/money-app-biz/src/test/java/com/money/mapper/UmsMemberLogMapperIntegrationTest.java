package com.money.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberLog;
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
class UmsMemberLogMapperIntegrationTest {
    @Autowired private UmsMemberLogMapper memberLogMapper;
    @Test void mapperCrudUsesUmsLocalMemberAssetLedger() {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMemberLog log = new UmsMemberLog();
        log.setMemberId(90000001L); log.setType("BALANCE"); log.setOperateType("RECHARGE");
        log.setAmount(new BigDecimal("12.34")); log.setAfterAmount(new BigDecimal("56.78"));
        log.setRemark("mapper regression"); log.setCreateBy("test"); log.setTenantId("0");
        log.setOrderNo("AD243-" + suffix); log.setRealAmount(new BigDecimal("10.00"));
        log.setMemberName("测试会员"); log.setMemberPhone("13800000000");
        assertThat(memberLogMapper.insert(log)).isEqualTo(1); assertThat(log.getId()).isNotNull();
        UmsMemberLog persisted = memberLogMapper.selectById(log.getId());
        assertThat(persisted).extracting(UmsMemberLog::getMemberId, UmsMemberLog::getType,
                UmsMemberLog::getOperateType, UmsMemberLog::getAmount, UmsMemberLog::getAfterAmount,
                UmsMemberLog::getOrderNo, UmsMemberLog::getRealAmount, UmsMemberLog::getMemberName,
                UmsMemberLog::getMemberPhone, UmsMemberLog::getTenantId)
                .containsExactly(90000001L, "BALANCE", "RECHARGE", new BigDecimal("12.34"),
                        new BigDecimal("56.78"), log.getOrderNo(), new BigDecimal("10.00"),
                        "测试会员", "13800000000", "0");
        log.setAfterAmount(new BigDecimal("66.78")); assertThat(memberLogMapper.updateById(log)).isEqualTo(1);
        assertThat(memberLogMapper.selectById(log.getId()).getAfterAmount()).isEqualByComparingTo("66.78");
        assertThat(memberLogMapper.deleteById(log.getId())).isEqualTo(1);
    }
}
