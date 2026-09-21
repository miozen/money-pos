package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 会员多品牌身份矩阵表
 * </p>
 */
@Data
@TableName("ums_member_brand_level")
@Schema(description = "会员多品牌身份矩阵表")
public class UmsMemberBrandLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "会员ID")
    private Long memberId;

    @Schema(description = "品牌名称或ID")
    private String brand;

    @Schema(description = "字典中的等级Code (如：WANYI_06)")
    private String levelCode;

    // 🌟 核心修复：增加租户ID字段，解决底层拦截器报错
    @Schema(description = "租户ID")
    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
