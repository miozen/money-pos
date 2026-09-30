package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @TableName("oms_member_amount_receipt")
public class OmsMemberAmountReceipt {
    @TableId(type = IdType.AUTO) private Long id;
    private String receiptNo; private String requestNo; private String receiptType; private Long memberId; private String brandId;
    private Long amountRightId; private String sourcePickupNo; private String sourceReceiptNo;
    private BigDecimal totalAmount; private BigDecimal rightDeductAmount; private BigDecimal supplementAmount; private BigDecimal costAmount;
    private String status; private Long tenantId;
    private LocalDateTime createTime;
}
