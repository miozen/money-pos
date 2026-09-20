package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GmsInventoryOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;       // 单据编号
    private String type;          // 单据类型 (INBOUND, CHECK, OUTBOUND)
    private BigDecimal totalAmount; // 单据总金额
    private String status;        // 状态 (PENDING, COMPLETED)
    private String remark;        // 备注
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long tenantId;
}
