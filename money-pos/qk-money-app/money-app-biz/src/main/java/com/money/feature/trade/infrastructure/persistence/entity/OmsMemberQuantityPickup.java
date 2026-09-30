package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("oms_member_quantity_pickup")
public class OmsMemberQuantityPickup {
    @TableId(type = IdType.AUTO) private Long id;
    private String pickupNo; private String requestNo; private Long memberId; private String status; private Long tenantId;
    private LocalDateTime createTime;
}
