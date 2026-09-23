package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ums_member_target_plan")
public class UmsMemberTargetPlan {
    @TableId(type = IdType.AUTO) private Long id;
    private Long memberId;
    private String brandId;
    private String currentLevelCodeSnapshot;
    private String targetTierCodeSnapshot;
    private String targetTierNameSnapshot;
    private String targetPricingLevelCodeSnapshot;
    private Integer targetRankSnapshot;
    private BigDecimal targetAmount;
    private BigDecimal progressAmount;
    private String status;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    private String remark;
    private String createRequestNo;
    private Long tenantId;
}
