package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.home.infrastructure.persistence.entity.OmsDailySummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日经营快照 Mapper
 * 职责：专职负责 oms_daily_summary 表的基础读写
 */
@Mapper
public interface OmsDailySummaryMapper extends BaseMapper<OmsDailySummary> {

    @Insert("INSERT INTO oms_daily_summary (record_date, sales_amount, order_count, profit_amount, asp, "
            + "inventory_value, new_member_count, create_time, update_time) VALUES "
            + "(#{recordDate}, #{salesAmount}, #{orderCount}, #{profitAmount}, #{asp}, "
            + "#{inventoryValue}, #{newMemberCount}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE record_date = VALUES(record_date)")
    int insertIfAbsent(OmsDailySummary summary);

    @Insert("INSERT INTO oms_daily_summary (record_date, sales_amount, order_count, profit_amount, asp, "
            + "inventory_value, new_member_count, create_time, update_time) VALUES "
            + "(#{recordDate}, #{salesAmount}, #{orderCount}, #{profitAmount}, #{asp}, "
            + "#{inventoryValue}, #{newMemberCount}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE sales_amount = VALUES(sales_amount), order_count = VALUES(order_count), "
            + "profit_amount = VALUES(profit_amount), asp = VALUES(asp), "
            + "inventory_value = VALUES(inventory_value), new_member_count = VALUES(new_member_count), "
            + "update_time = NOW()")
    int upsertSnapshot(OmsDailySummary summary);

    // 继承 BaseMapper 后，自带 insert, update, selectList 等能力，无需手写基础 SQL。
    // 后续如果需要复杂的跨表聚合查询，可以补充在这里。
}
