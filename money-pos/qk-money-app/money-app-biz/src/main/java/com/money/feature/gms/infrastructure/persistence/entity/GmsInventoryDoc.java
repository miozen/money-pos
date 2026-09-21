package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("gms_inventory_doc")
public class GmsInventoryDoc extends BaseEntity {
    private String docNo;
    private String docType;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private String operator;
    private String remark;
    private Long tenantId;
}
