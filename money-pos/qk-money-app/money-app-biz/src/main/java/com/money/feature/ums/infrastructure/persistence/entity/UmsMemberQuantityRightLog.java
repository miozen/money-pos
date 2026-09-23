package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ums_member_quantity_right_log")
public class UmsMemberQuantityRightLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long rightId;
    private String action;
    private Integer quantityDelta;
    private Integer beforeQuantity;
    private Integer afterQuantity;
    private String requestNo;
    private String sourceType;
    private String sourceNo;
    private String operatorName;
    private String reason;
    private Long tenantId;
}
