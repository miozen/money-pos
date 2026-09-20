package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GmsMemberTransaction {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long memberId;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String orderNo;
    private String remark;
    private LocalDateTime createTime;
    private Long tenantId;
}
