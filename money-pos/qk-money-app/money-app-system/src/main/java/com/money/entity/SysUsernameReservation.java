package com.money.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Prevents a renamed login name from being reused while its previously issued JWT may still be valid. */
@Getter
@Setter
@TableName("sys_username_reservation")
public class SysUsernameReservation extends BaseEntity {

    private String username;
    private LocalDateTime expiresAt;
    private Long tenantId;
}
