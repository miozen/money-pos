package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("ums_member_amount_right")
public class UmsMemberAmountRight {
    @TableId(type = IdType.AUTO) private Long id;
    private Long memberId;
    private String brandId;
    private String tierCodeSnapshot;
    private String tierNameSnapshot;
    private String pricingLevelCodeSnapshot;
    private BigDecimal grantedAmount;
    private BigDecimal remainingAmount;
    private String sourceReceiptNo;
    private String createRequestNo;
    private String status;
    private Long tenantId;
}
