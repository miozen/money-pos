package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ums_member_log")
public class UmsMemberLog {
 @TableId(type = IdType.AUTO) private Long id; private Long memberId; private String type; private String operateType;
 private BigDecimal amount; private BigDecimal afterAmount; private String remark; private String createBy;
 private LocalDateTime createTime; private String tenantId; private String orderNo; private BigDecimal realAmount;
 private String memberName; private String memberPhone;
}
