package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("oms_member_quantity_pickup_item")
public class OmsMemberQuantityPickupItem {
    @TableId(type = IdType.AUTO) private Long id;
    private String pickupNo; private Long quantityRightId; private Long goodsId; private Integer quantity; private Long tenantId;
}
