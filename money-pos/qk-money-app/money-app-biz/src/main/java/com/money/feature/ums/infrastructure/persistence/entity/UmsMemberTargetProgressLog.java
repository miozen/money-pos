package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("ums_member_target_progress_log")
public class UmsMemberTargetProgressLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long planId;
    private String action;
    private BigDecimal amountDelta;
    private BigDecimal beforeAmount;
    private BigDecimal afterAmount;
    private String requestNo;
    private String sourceType;
    private String sourceNo;
    private String operatorName;
    private String reason;
    private Long tenantId;
}
