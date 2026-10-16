package com.money.service;

import com.money.dto.ChangeUsernameDTO;
import com.money.dto.SysUserDTO;
import com.money.entity.SysUser;
import com.money.entity.SysUsernameReservation;
import com.money.mapper.SysUsernameReservationMapper;
import com.money.web.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysUserUsernameChangeIntegrationTest {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysUsernameReservationMapper reservationMapper;

    @Test
    void superAdminRenameReservesOldLoginNameAndPreventsReuse() {
        SysUser superAdmin = sysUserService.getByUsername("vana");
        assertThat(superAdmin).isNotNull();
        String newUsername = "vana-" + UUID.randomUUID().toString().substring(0, 8);
        ChangeUsernameDTO change = new ChangeUsernameDTO();
        change.setUsername(newUsername);

        sysUserService.changeOwnUsername(superAdmin.getId(), change);

        assertThat(sysUserService.getByUsername("vana")).isNull();
        assertThat(sysUserService.getByUsername(newUsername)).extracting(SysUser::getId).isEqualTo(superAdmin.getId());
        SysUsernameReservation reservation = reservationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUsernameReservation>()
                        .eq(SysUsernameReservation::getUsername, "vana"));
        assertThat(reservation).isNotNull();
        assertThat(reservation.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));

        SysUserDTO duplicate = new SysUserDTO();
        duplicate.setUsername("vana");
        assertThatThrownBy(() -> sysUserService.add(duplicate))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("保护期");
    }

    @Test
    void ordinaryUserCannotChangeOwnLoginName() {
        SysUser user = sysUserService.getByUsername("guest");
        assertThat(user).isNotNull();
        ChangeUsernameDTO change = new ChangeUsernameDTO();
        change.setUsername("other-name");

        assertThatThrownBy(() -> sysUserService.changeOwnUsername(user.getId(), change))
                .isInstanceOf(BaseException.class);
    }
}
