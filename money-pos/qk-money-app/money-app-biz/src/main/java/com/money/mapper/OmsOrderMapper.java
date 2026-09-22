package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    @Update("UPDATE oms_order SET " +
            "final_sales_amount = IFNULL(final_sales_amount, 0) - #{refundSales}, " +
            "cost_amount = IFNULL(cost_amount, 0) - #{refundCost}, " +
            "coupon_amount = IFNULL(coupon_amount, 0) - #{refundCoupon}, " +
            "actual_coupon_deduct = IFNULL(actual_coupon_deduct, 0) - #{refundCoupon}, " +
            "status = #{status} " +
            "WHERE order_no = #{orderNo}")
    int applyPartialRefund(@Param("orderNo") String orderNo,
                           @Param("refundSales") BigDecimal refundSales,
                           @Param("refundCost") BigDecimal refundCost,
                           @Param("refundCoupon") BigDecimal refundCoupon,
                           @Param("status") String status);

    @Update("UPDATE oms_order SET " +
            "final_sales_amount = 0, " +
            "cost_amount = 0, " +
            "coupon_amount = 0, " +
            "actual_coupon_deduct = 0, " +
            "waived_coupon_amount = 0, " +
            "use_voucher_amount = 0, " +
            "manual_discount_amount = 0, " +
            "status = #{status} " +
            "WHERE order_no = #{orderNo}")
    int updateRefundStatusToFull(@Param("orderNo") String orderNo, @Param("status") String status);

    @Update("UPDATE oms_order SET status = 'REFUNDED' " +
            "WHERE order_no = #{orderNo} " +
            "AND (SELECT SUM(quantity) FROM oms_order_detail WHERE order_no = #{orderNo}) = " +
            "    (SELECT SUM(IFNULL(return_quantity, 0)) FROM oms_order_detail WHERE order_no = #{orderNo})")
    void checkAndUpgradeToFullRefund(@Param("orderNo") String orderNo);

    List<OmsSalesDataVO.TimeTrafficVO> getMonthlyTrafficAnalysis(int days, double divisor);

    List<Map<String, Object>> getCashierRiskSummary(LocalDateTime startTime, LocalDateTime endTime);

    // 🌟 核心提取：新增退款计算 (支付总额 - 最终销售总额)
    @Select("<script>" +
            "SELECT " +
            "IFNULL(SUM(manual_discount_amount), 0) AS manualDiscount, " +
            "IFNULL(SUM(use_voucher_amount), 0) AS voucherDiscount, " +
            "IFNULL(SUM(actual_coupon_deduct), 0) AS memberCouponPay, " +
            "IFNULL(SUM(waived_coupon_amount), 0) AS waivedCouponAmount, " +
            "IFNULL(SUM(CASE WHEN use_voucher_amount > 0 THEN 1 ELSE 0 END), 0) AS voucherCount, " +
            "IFNULL(SUM(CASE WHEN pay_amount > final_sales_amount THEN pay_amount - final_sales_amount ELSE 0 END), 0) AS refundAmount " +
            "FROM oms_order " +
            "WHERE create_time &gt;= #{startTime} AND create_time &lt;= #{endTime} " +
            "  AND status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'RETURN') " +
            "<if test='cashierName != null and cashierName != \"全部收银员\"'> " +
            "  AND create_by = #{cashierName} " +
            "</if>" +
            "</script>")
    Map<String, Object> getShiftDiscountStats(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, @Param("cashierName") String cashierName);
}
