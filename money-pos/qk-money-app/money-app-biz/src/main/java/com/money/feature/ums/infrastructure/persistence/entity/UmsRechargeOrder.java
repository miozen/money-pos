package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ums_recharge_order")
public class UmsRechargeOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long memberId;
    private String type;
    private BigDecimal amount;
    private BigDecimal giftCoupon;
    private BigDecimal realAmount;
    private String status;
    private String remark;
    private String tenantId;
    private LocalDateTime createTime;
}
