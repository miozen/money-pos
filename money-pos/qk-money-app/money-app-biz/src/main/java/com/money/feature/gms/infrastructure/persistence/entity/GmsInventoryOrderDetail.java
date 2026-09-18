package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** GMS 库存单明细持久化记录，不作为跨 Feature 契约暴露。 */
@Data
public class GmsInventoryOrderDetail {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long goodsId;
    private Integer qty;
    private BigDecimal price;
    private LocalDateTime createTime;
    private Long tenantId;
}
