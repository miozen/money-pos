package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 库存变动流水台账（数量与资产双轨账本）。 */
@Data
public class GmsStockLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long goodsId; private String goodsName; private String goodsBarcode; private String type;
    private Integer quantity; private Integer afterQuantity; private BigDecimal costPriceSnapshot;
    private BigDecimal impactAmount; private String orderNo; private String remark;
    private LocalDateTime createTime; private String creator; private Long tenantId;
}
