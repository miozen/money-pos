package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pos_coupon_rule")
public class PosCouponRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private Integer status;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String tenantId;
}
