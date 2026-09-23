package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("ums_brand_benefit_tier")
public class UmsBrandBenefitTier {
    @TableId(type = IdType.AUTO) private Long id;
    private String brandId;
    private String tierCode;
    private String tierName;
    private BigDecimal configuredAmount;
    private String pricingLevelCode;
    private Integer rankValue;
    private Boolean enabled;
    private Integer sortNo;
    private String remark;
    private Long tenantId;
}
