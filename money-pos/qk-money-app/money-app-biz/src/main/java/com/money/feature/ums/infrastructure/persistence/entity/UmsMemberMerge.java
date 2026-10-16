package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ums_member_merge")
public class UmsMemberMerge {
    @TableId(type = IdType.AUTO) private Long id;
    private String mergeNo; private Long sourceMemberId; private Long targetMemberId; private String reason;
    private BigDecimal sourceBalanceBefore; private BigDecimal targetBalanceBefore; private BigDecimal transferredBalance;
    private Integer transferredUnusedCouponCount; private String status; private String operatorName;
    private LocalDateTime createTime; private LocalDateTime completeTime; private Long tenantId;
}
