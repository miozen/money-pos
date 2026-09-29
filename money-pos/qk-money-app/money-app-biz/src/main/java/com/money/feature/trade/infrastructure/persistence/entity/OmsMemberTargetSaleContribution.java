package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data @TableName("oms_member_target_sale_contribution")
public class OmsMemberTargetSaleContribution {
    @TableId(type = IdType.AUTO) private Long id;
    private Long targetPlanId; private String orderNo; private Long memberId; private String brandId;
    private BigDecimal contributionAmount; private String status; private Long tenantId;
}
