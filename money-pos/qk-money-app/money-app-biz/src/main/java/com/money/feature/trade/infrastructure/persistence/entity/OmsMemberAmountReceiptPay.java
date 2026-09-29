package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data @TableName("oms_member_amount_receipt_pay")
public class OmsMemberAmountReceiptPay {
    @TableId(type = IdType.AUTO) private Long id;
    private String receiptNo; private String payMethodCode; private String payMethodName; private String payTag;
    private BigDecimal payAmount; private BigDecimal originalAmount; private BigDecimal netAmount; private BigDecimal changeAllocated; private Long tenantId;
}
