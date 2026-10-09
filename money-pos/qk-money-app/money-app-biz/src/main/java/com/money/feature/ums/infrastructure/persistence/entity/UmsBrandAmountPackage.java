package com.money.feature.ums.infrastructure.persistence.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.math.BigDecimal;
@Data @TableName("ums_brand_amount_package") public class UmsBrandAmountPackage { @TableId(type=IdType.AUTO) private Long id; private String brandId,packageCode,packageName,pricingLevelCode,remark,legacyTierCode; private BigDecimal purchaseAmount,benefitAmount; private Boolean enabled; private Integer sortNo; private Long tenantId; }
