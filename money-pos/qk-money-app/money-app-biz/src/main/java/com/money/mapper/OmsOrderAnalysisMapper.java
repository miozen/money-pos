package com.money.mapper;

import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO.GoodsSalesRankVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.CategorySalesVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 🌟 经营分析集市 Mapper (OLAP)
 */
@Mapper
public interface OmsOrderAnalysisMapper {

    @Select("SELECT " +
            "  CASE " +
            "    WHEN #{dimension} = 'MONTHLY' THEN DATE_FORMAT(create_time, '%Y-%m') " +
            "    WHEN #{dimension} = 'WEEKLY' THEN DATE_FORMAT(create_time, '%x-W%v') " +
            "    ELSE DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "  END AS period, " +
            "  COUNT(id) AS orderCount, " +
            "  SUM(IFNULL(final_sales_amount, 0)) AS netSalesAmount, " +
            "  SUM(IFNULL(cost_amount, 0)) AS costAmount, " +
            "  SUM((SELECT SUM(IFNULL(quantity, 0) - IFNULL(return_quantity, 0)) FROM oms_order_detail d WHERE d.order_no = o.order_no)) AS goodsCount " +
            "FROM oms_order o " +
            // 🌟 洗缩：坚决剔除 REFUNDED 状态，净化基础分析池
            "WHERE status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "  AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY period ORDER BY period ASC")
    List<AnalysisAtomicDataDTO> getPeriodAtomicStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("dimension") String dimension);

    // 🌟 修复：精准提取 d.goods_id AS goodsId 给前端趋势联动用
    @Select("SELECT " +
            "  d.goods_id AS goodsId, " +
            "  d.goods_name AS goodsName, " +
            "  SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS salesQty, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING salesQty > 0 " +
            "ORDER BY salesQty DESC LIMIT 50")
    List<GoodsSalesRankVO> getTopGoodsRank(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT " +
            "  d.brand_id AS brandId, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.brand_id " +
            "HAVING salesAmount > 0 " +
            "ORDER BY salesAmount DESC")
    List<java.util.Map<String, Object>> getBrandSalesAmounts(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT '满减券' AS ruleType, IFNULL(remark, '通用满减活动') AS ruleName, COUNT(id) AS usedCount, SUM(IFNULL(use_voucher_amount, 0)) AS totalDiscountGived, SUM(IFNULL(final_sales_amount, 0)) AS totalRevenueBrought " +
            "FROM oms_order WHERE status IN ('PAID', 'PARTIAL_REFUNDED') AND create_time >= #{startTime} AND create_time <= #{endTime} AND IFNULL(use_voucher_amount, 0) > 0 GROUP BY ruleName " +
            "UNION ALL " +
            "SELECT '会员资产' AS ruleType, '会员专属券核销' AS ruleName, COUNT(id) AS usedCount, SUM(IFNULL(actual_coupon_deduct, 0)) AS totalDiscountGived, SUM(IFNULL(final_sales_amount, 0)) AS totalRevenueBrought " +
            "FROM oms_order WHERE status IN ('PAID', 'PARTIAL_REFUNDED') AND create_time >= #{startTime} AND create_time <= #{endTime} AND IFNULL(actual_coupon_deduct, 0) > 0")
    List<MarketingRoiVO> getMarketingRoiStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT " +
            "  IFNULL(c.name, '未分类') AS categoryName, " +
            "  SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS salesQty, " +
            "  SUM((d.quantity - IFNULL(d.return_quantity, 0)) * IFNULL(d.goods_price, 0)) AS salesAmount " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "LEFT JOIN gms_goods_category c ON d.category_id = c.id " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "  AND o.create_time >= #{startTime} AND o.create_time <= #{endTime} " +
            "GROUP BY d.category_id, categoryName " +
            "HAVING salesQty > 0 " +
            "ORDER BY salesAmount DESC")
    List<CategorySalesVO> getCategorySalesDistribution(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // ==========================================
    // 🌟 P0-2 引擎：按日聚合会员与散客经营体征
    // ==========================================
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr, " +
            // 🌟 核心修复点 1：精准散客判定，兼容底层默认值0与历史的vip标识兜底，拒绝“伪会员”
            "  CASE WHEN IFNULL(member_id, 0) > 0 OR IFNULL(vip, 0) = 1 THEN 1 ELSE 0 END AS isMember, " +
            "  COUNT(id) AS orderCount, " +
            "  SUM(IFNULL(final_sales_amount, 0)) AS salesAmount " +
            "FROM oms_order " +
            "WHERE status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "  AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY dateStr, isMember " +
            "ORDER BY dateStr ASC")
    List<com.money.dto.OmsOrder.OmsSalesDataVO.DailyMemberStatDTO> getDailyMemberStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // ==========================================
    // 🌟 P0-3 引擎：查询指定单品集合的每日净销量走势
    // ==========================================
    @Select("<script>" +
            "SELECT DATE_FORMAT(o.create_time, '%Y-%m-%d') AS dateStr, " +
            "  d.goods_id AS goodsId, " +
            "  d.goods_name AS goodsName, " +
            "  SUM(GREATEST(d.quantity - IFNULL(d.return_quantity, 0), 0)) AS salesQty " +
            "FROM oms_order_detail d " +
            "INNER JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.status IN ('PAID', 'PARTIAL_REFUNDED') " +
            "  AND o.create_time &gt;= #{startTime} AND o.create_time &lt;= #{endTime} " +
            "  AND d.goods_id IN " +
            "  <foreach item='id' collection='goodsIds' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY dateStr, d.goods_id, d.goods_name " +
            "ORDER BY dateStr ASC" +
            "</script>")
    List<com.money.dto.OmsOrder.OmsSalesDataVO.DailyGoodsStatDTO> getDailyGoodsStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("goodsIds") List<Long> goodsIds);
}
