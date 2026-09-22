package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderPay;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OmsOrderPayMapper extends BaseMapper<OmsOrderPay> {

    /**
     * 🌟 高性能聚合引擎：按天、按渠道、按标签汇总支付流水
     * 🌟 V5.0 终极财务版：全面启用 net_amount 净额快照，完美剔除找零误差，向下兼容历史数据
     */
    @Select("SELECT DATE_FORMAT(o.create_time, '%Y-%m-%d') AS dateStr, " +
            "p.pay_method_code AS methodCode, " +
            "p.pay_tag AS payTag, " +
            // 提取实收毛额（含找零）
            "SUM(IFNULL(p.original_amount, p.pay_amount)) AS totalAmount, " +
            // 🌟 核心排雷：提取真正的入账净额（扣除找零），并剔除全额退款的单子
            "SUM(CASE WHEN o.status IN ('REFUNDED', 'RETURN') THEN 0 ELSE IFNULL(p.net_amount, p.pay_amount) END) AS netAmount " +
            "FROM oms_order_pay p " +
            "JOIN oms_order o ON p.order_no = o.order_no " +
            "WHERE o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "  AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'RETURN') " +
            "GROUP BY DATE(o.create_time), p.pay_method_code, p.pay_tag")
    List<Map<String, Object>> getDailyPaySummary(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 🌟 交接班专属：高精度资金流扫荡 (钱箱对账绝对真理)
     */
    @Select("<script>" +
            "SELECT p.pay_method_code AS methodCode, " +
            "p.pay_tag AS payTag, " +
            "SUM(IFNULL(p.original_amount, p.pay_amount)) AS totalAmount, " +
            // 🌟 核心排雷：这里决定了交接班打印小票上的现金总额，必须用 net_amount！
            "SUM(CASE WHEN o.status IN ('REFUNDED', 'RETURN') THEN 0 ELSE IFNULL(p.net_amount, p.pay_amount) END) AS netAmount " +
            "FROM oms_order_pay p " +
            "JOIN oms_order o ON p.order_no = o.order_no " +
            "WHERE o.create_time &gt;= #{startTime} AND o.create_time &lt;= #{endTime} " +
            "  AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'RETURN') " +
            "<if test='cashierName != null and cashierName != \"全部收银员\"'> " +
            "  AND o.create_by = #{cashierName} " +
            "</if>" +
            "GROUP BY p.pay_method_code, p.pay_tag" +
            "</script>")
    List<Map<String, Object>> getShiftPayStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("cashierName") String cashierName);
}
