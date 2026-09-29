package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data @TableName("oms_member_target_receipt")
public class OmsMemberTargetReceipt {
    @TableId(type = IdType.AUTO) private Long id;
    private String receiptNo; private String requestNo; private Long targetPlanId; private Long memberId;
    private String receiptType; private BigDecimal amount; private String status; private String reason; private Long tenantId;
}
