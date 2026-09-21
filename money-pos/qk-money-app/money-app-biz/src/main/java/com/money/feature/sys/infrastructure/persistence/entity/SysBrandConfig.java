package com.money.feature.sys.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 品牌定价策略配置表。 */
@Data
@TableName("sys_brand_config")
@Schema(description = "品牌定价策略配置表")
public class SysBrandConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "品牌名称或ID")
    private String brand;

    @Schema(description = "是否开启会员券联动(true=绿叶模式, false=纯价模式)")
    private Boolean couponEnabled;

    @Schema(description = "该品牌绑定的字典Code列表(逗号分隔)")
    private String levelCodes;

    @Schema(description = "租户ID(解决多租户底层拦截报错)")
    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
