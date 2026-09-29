package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data @TableName("oms_member_amount_pickup_item")
public class OmsMemberAmountPickupItem {
    @TableId(type = IdType.AUTO) private Long id;
    private String pickupNo; private Long goodsId; private Integer quantity; private BigDecimal unitPrice; private BigDecimal lineAmount; private BigDecimal purchasePrice; private Long tenantId;
}
