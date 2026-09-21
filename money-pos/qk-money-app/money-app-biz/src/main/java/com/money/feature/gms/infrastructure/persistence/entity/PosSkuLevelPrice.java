package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("pos_sku_level_price")
public class PosSkuLevelPrice {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    // 字典中的等级 Code (例如：HJ_VIP, WANYI_01)
    private String levelId;

    // 该等级对应的会员价 (最终收取的钱)
    private BigDecimal memberPrice;

    // 🌟 核心新增：该等级对应的专属会员券额度 (用于绿叶等需要券的品牌)
    private BigDecimal memberCoupon;

    private String tenantId;
}
