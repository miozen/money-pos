package com.money.feature.gms.infrastructure.persistence.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("gms_goods") @Schema(description="商品表")
public class GmsGoods extends BaseEntity {
 private Long brandId; private Long categoryId; private String barcode; private String name; private String pinyin; private String pic; private String unit; private String size; private String description; private BigDecimal purchasePrice; private BigDecimal avgCostPrice; private BigDecimal lastPurchasePrice; private BigDecimal salePrice; private BigDecimal vipPrice; private BigDecimal coupon; private Long stock; private Long sales; private String status; private Long tenantId; private Integer isDiscountParticipable; private String mnemonicCode; private Integer isCombo;
}
