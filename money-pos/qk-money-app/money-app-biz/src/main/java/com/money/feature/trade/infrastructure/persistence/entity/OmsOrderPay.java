package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OmsOrderPay {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String payMethodName;
    private String payTag;
    private String payMethodCode;
    private BigDecimal payAmount;
    private BigDecimal originalAmount;
    private BigDecimal netAmount;
    private BigDecimal changeAllocated;
    private LocalDateTime createTime;
}
