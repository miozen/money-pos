package com.money.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.dto.OmsOrder.ProfitAuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🌟 审计风控集市 Mapper (V2.1 财务穿透版)
 * 职责：专职处理财务毛利审计、单品利润核算及收银风险监测
 */
@Mapper
public interface OmsOrderAuditMapper {

    /**
     * 核心审计：获取毛利快照分页数据 (穿透至订单明细层)
     * 解决前端：价格快照隔离区、利润核算区数据缺失问题
     */
    @Select("<script>" +
            "SELECT " +
            "  order_no AS orderNo, " +
            "  goods_name AS goodsName, " +
            "  create_time AS createTime, " +
            "  IFNULL(sale_price, 0) AS salePrice, " +
            "  IFNULL(goods_price, 0) AS goodsPrice, " +
            "  IFNULL(purchase_price, 0) AS purchasePrice, " +
            "  (IFNULL(goods_price, 0) - IFNULL(purchase_price, 0)) AS unitProfit, " +
            "  CASE WHEN IFNULL(goods_price, 0) > 0 " +
            "       THEN (IFNULL(goods_price, 0) - IFNULL(purchase_price, 0)) / IFNULL(goods_price, 0) " +
            "       ELSE 0 END AS profitMargin, " +
            "  CASE WHEN (purchase_price IS NULL OR purchase_price &lt;= 0) THEN 1 ELSE 0 END AS isMissingCost " +
            "FROM oms_order_detail " +
            // 🌟 修复：统一标准状态集
            "WHERE status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'RETURN') " +
            "  <if test='orderNo != null and orderNo != \"\"'> " +
            "    AND order_no = #{orderNo} " +
            "  </if> " +
            "  <if test='status == \"ANOMALY\"'> " +
            "    AND (purchase_price IS NULL OR purchase_price &lt;= 0 OR (goods_price - purchase_price) &lt; 0) " +
            "  </if> " +
            "ORDER BY create_time DESC" +
            "</script>")
    Page<ProfitAuditVO> getProfitAuditPage(Page<?> page,
                                           @Param("orderNo") String orderNo,
                                           @Param("status") String status);

    /**
     * 收银风险概览：按收银员统计优惠与退款异常
     */
    @Select("SELECT " +
            "  create_by AS cashierName, " +
            "  COUNT(id) AS orderCount, " +
            "  SUM(IFNULL(manual_discount_amount, 0)) AS manualDiscountAmount, " +
            "  SUM(CASE WHEN status IN ('PARTIAL_REFUNDED', 'REFUNDED', 'RETURN') THEN 1 ELSE 0 END) AS refundCount " +
            "FROM oms_order " +
            "WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY create_by")
    List<Map<String, Object>> getCashierRiskSummary(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 异常单据清单：直接从订单主表识别成本缺失或毛利倒挂
     */
    @Select("SELECT " +
            "  order_no AS orderNo, " +
            "  DATE_FORMAT(create_time, '%m-%d %H:%i') AS createTime, " +
            "  create_by AS cashier, " +
            "  pay_amount AS payAmount, " +
            "  cost_amount AS costAmount, " +
            "  (IFNULL(final_sales_amount, 0) - IFNULL(cost_amount, 0)) AS profit, " +
            "  CASE " +
            "    WHEN (pay_amount >= 10 AND (cost_amount IS NULL OR cost_amount <= 0)) THEN '缺失成本预警' " +
            "    WHEN ((IFNULL(final_sales_amount, 0) - IFNULL(cost_amount, 0)) < 0 AND pay_amount > 0) THEN '倒挂亏损交易' " +
            "    WHEN IFNULL(manual_discount_amount, 0) > 50 THEN '大额手工放水' " +
            "    ELSE '其他风险' " +
            "  END AS riskType " +
            "FROM oms_order " +
            "WHERE (create_time >= #{startTime} AND create_time <= #{endTime}) " +
            "  AND ( " +
            "    (pay_amount >= 10 AND (cost_amount IS NULL OR cost_amount <= 0)) " +
            "    OR ((IFNULL(final_sales_amount, 0) - IFNULL(cost_amount, 0)) < 0 AND pay_amount > 0) " +
            "    OR IFNULL(manual_discount_amount, 0) > 50 " +
            "  ) " +
            "ORDER BY profit ASC LIMIT 50")
    List<Map<String, Object>> getAbnormalOrderList(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
