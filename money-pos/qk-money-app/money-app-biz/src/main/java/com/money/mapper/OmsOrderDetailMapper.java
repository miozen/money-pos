package com.money.mapper;

import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 订单明细表 Mapper 接口 (大一统原子操作版 - V3.0 动态沙盘版)
 * </p>
 */
public interface OmsOrderDetailMapper extends BaseMapper<OmsOrderDetail> {

    @Update("UPDATE oms_order_detail " +
            "SET return_quantity = IFNULL(return_quantity, 0) + #{returnQty}, " +
            "    status = CASE " +
            "               WHEN quantity <= IFNULL(return_quantity, 0) + #{returnQty} THEN 'REFUNDED' " +
            "               ELSE status " +
            "             END " +
            "WHERE id = #{detailId} " +
            "  AND (quantity - IFNULL(return_quantity, 0) >= #{returnQty})")
    int refundGoodsAtomically(@Param("detailId") Long detailId, @Param("returnQty") int returnQty);

    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%m-%d') AS date, " +
            "SUM(IFNULL(final_sales_amount, pay_amount)) AS sales, " +
            "SUM(IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)) AS profit " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if> " +
            "<if test='endTime != null'> AND create_time &lt; #{endTime} </if> " +
            "GROUP BY DATE(create_time), DATE_FORMAT(create_time, '%m-%d') " +
            "ORDER BY DATE(create_time) ASC" +
            "</script>")
    List<com.money.dto.Home.TrendChartVO> getTrendData(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("<script>" +
            "SELECT IFNULL(gb.name, '无品牌/未知') AS name, " +
            "SUM((ood.quantity - IFNULL(ood.return_quantity, 0)) * IFNULL(ood.goods_price, 0)) AS value " +
            "FROM oms_order_detail ood " +
            "LEFT JOIN gms_brand gb ON ood.brand_id = gb.id " +
            "JOIN oms_order o ON ood.order_no = o.order_no " +
            "WHERE ood.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "<if test='startTime != null'> AND o.create_time &gt;= #{startTime} </if> " +
            "<if test='endTime != null'> AND o.create_time &lt; #{endTime} </if> " +
            "GROUP BY gb.id, gb.name " +
            "HAVING value > 0" +
            "</script>")
    List<com.money.dto.Home.BrandPieVO> getBrandPieData(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT d.goods_name AS goodsName, " +
            "SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS totalQuantity, " +
            "SUM(d.goods_price * (d.quantity - IFNULL(d.return_quantity, 0))) AS totalSales, " +
            "SUM((d.goods_price - IFNULL(d.purchase_price, 0)) * (d.quantity - IFNULL(d.return_quantity, 0))) AS totalProfit " +
            "FROM oms_order_detail d " +
            "JOIN oms_order o ON o.order_no = d.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "AND o.create_time >= #{startTime} " +
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING totalQuantity > 0 " +
            "ORDER BY totalProfit DESC " +
            "LIMIT 50")
    List<com.money.dto.Finance.FinanceDataVO.ProfitRankVO> getProfitRankingData(@Param("startTime") LocalDateTime startTime);

    /**
     * 🌟 交接班专属：品牌贡献度 (已剔除退款退货，包含真实核销券耗)
     */
    @Select("<script>" +
            "SELECT ood.brand_id AS brandId, " +
            "SUM((ood.quantity - IFNULL(ood.return_quantity, 0)) * IFNULL(ood.goods_price, 0)) AS revenue, " +
            "IFNULL(SUM(CASE WHEN ood.quantity > 0 THEN (ood.coupon / ood.quantity) * (ood.quantity - IFNULL(ood.return_quantity, 0)) ELSE 0 END), 0) AS couponConsumption " +
            "FROM oms_order_detail ood " +
            "JOIN oms_order o ON ood.order_no = o.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "  AND o.create_time &gt;= #{startTime} AND o.create_time &lt;= #{endTime} " +
            "<if test='cashierName != null and cashierName != \"全部收银员\"'> " +
            "  AND o.create_by = #{cashierName} " +
            "</if> " +
            "GROUP BY ood.brand_id " +
            "HAVING revenue > 0 " +
            "ORDER BY revenue DESC" +
            "</script>")
    List<Map<String, Object>> getShiftBrandContribution(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("cashierName") String cashierName);
}
