package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pos_member_coupon")
public class PosMemberCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long ruleId;
    private String status;
    private String orderNo;
    private LocalDateTime getTime;
    private LocalDateTime useTime;
    private String tenantId;
}
