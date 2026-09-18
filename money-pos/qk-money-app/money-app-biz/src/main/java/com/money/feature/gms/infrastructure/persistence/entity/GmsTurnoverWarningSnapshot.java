package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** GMS 周转预警日快照持久化记录，不作为跨 Feature 契约暴露。 */
@Data
@TableName(value = "gms_turnover_warning_snapshot", autoResultMap = true)
public class GmsTurnoverWarningSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;
    private Integer replenishCount;
    private Integer deadStockCount;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private String topReplenishGoodsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private String topDeadStockGoodsJson;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
