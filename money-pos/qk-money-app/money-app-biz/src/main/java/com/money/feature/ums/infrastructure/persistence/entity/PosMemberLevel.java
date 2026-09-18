package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** UMS 会员等级持久化记录，不作为跨 Feature 契约暴露。 */
@Data
@TableName("pos_member_level")
public class PosMemberLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String levelName;
    private String tenantId;
}
