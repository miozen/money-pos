package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("gms_inventory_doc_item")
public class GmsInventoryDocItem extends BaseEntity {
    private String docNo;
    private Long goodsId;
    private String goodsName;
    private String barcode;
    private Integer changeQty;
    private BigDecimal costPrice;
    private Long preStock;
    private Long afterStock;
    private Long tenantId;
}