package com.money.feature.home.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 实体类：每日经营快照 (支撑大盘与决策引擎)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oms_daily_summary")
public class OmsDailySummary extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 记录日期 (精确到天，如 2026-03-19)
     */
    private LocalDate recordDate;

    /**
     * 当日实收营业额 (现金流)
     */
    private BigDecimal salesAmount;

    /**
     * 当日有效结单总数 (人气)
     */
    private Integer orderCount;

    /**
     * 当日净利润 (赚钱能力)
     */
    private BigDecimal profitAmount;

    /**
     * 当日客单价 ASP (客群质量)
     */
    private BigDecimal asp;

    /**
     * 当日打烊库存总成本 (压货资金)
     */
    private BigDecimal inventoryValue;

    /**
     * 当日会员充值总额 (新增负债)
     */
    private BigDecimal memberRecharge;

    /**
     * 当日新增会员数 (拉新能力)
     */
    private Integer newMemberCount;
}
