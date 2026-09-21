package com.money.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 会员表 Mapper 接口 (V4.0 资产防注入安全版)
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
public interface UmsMemberMapper extends BaseMapper<UmsMember> {

    /**
     * 🌟 V3.0 会员偏好榜：全状态覆盖，精准扣减退货，数据库底层秒级直出
     */
    @Select("SELECT d.goods_name AS goodsName, " +
            "SUM(d.quantity - IFNULL(d.return_quantity, 0)) AS buyCount " +
            "FROM oms_order_detail d " +
            "JOIN oms_order o ON d.order_no = o.order_no " +
            "WHERE o.member_id = #{memberId} " +
            "  AND o.status IN ('PAID', 'PARTIAL_REFUNDED', 'REFUNDED') " +
            "GROUP BY d.goods_id, d.goods_name " +
            "HAVING buyCount > 0 " +
            "ORDER BY buyCount DESC " +
            "LIMIT 20")
    List<Map<String, Object>> getTop20Goods(@Param("memberId") Long memberId);

    // 1. 土豪榜：累计消费 Top 50
    @Select("SELECT id, name, phone, consume_amount as amount FROM ums_member WHERE deleted = 0 ORDER BY consume_amount DESC LIMIT 50")
    List<com.money.dto.UmsMember.MemberRankVO> getTopConsumeMembers();

    // 2. 储值榜：当前余额 Top 50
    @Select("SELECT id, name, phone, balance as amount FROM ums_member WHERE deleted = 0 ORDER BY balance DESC LIMIT 50")
    List<com.money.dto.UmsMember.MemberRankVO> getTopBalanceMembers();

    // 3. 铁粉榜：到店频次 Top 50
    @Select("SELECT id, name, phone, consume_times as times FROM ums_member WHERE deleted = 0 ORDER BY consume_times DESC LIMIT 50")
    List<com.money.dto.UmsMember.MemberRankVO> getTopFrequencyMembers();

    /**
     * Keeps the legacy FIN asset-composition scope: all non-deleted members,
     * independent of the current tenant-line interceptor.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT " +
            "  IFNULL(SUM(balance), 0) as totalPrincipal, " +
            "  IFNULL(SUM(coupon), 0) as totalGift " +
            "FROM ums_member " +
            "WHERE deleted = 0")
    Map<String, Object> getFinanceAssetComposition();

    // ==========================================
    // 🌟 V4.0 新增：原子级安全资产更新接口 (完全替代原始 setSql)
    // ==========================================

    @Update("<script>" +
            "UPDATE ums_member SET " +
            "consume_amount = consume_amount + #{amount}, " +
            "consume_times = consume_times + 1 " +
            "<if test='coupon != null and coupon > 0'>, consume_coupon = consume_coupon + #{coupon}, coupon = coupon - #{coupon} </if> " +
            "WHERE id = #{id} " +
            "<if test='coupon != null and coupon > 0'> AND coupon >= #{coupon} </if>" +
            "</script>")
    int consumeAsset(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("coupon") BigDecimal coupon);

    @Update("UPDATE ums_member SET balance = balance - #{amount} WHERE id = #{id} AND balance >= #{amount}")
    int deductBalanceAtomically(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE ums_member SET balance = balance + #{amount} WHERE id = #{id}")
    int addBalanceAtomically(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("<script>" +
            "UPDATE ums_member SET " +
            "consume_amount = consume_amount - #{amount} " +
            "<if test='coupon != null and coupon > 0'>, coupon = coupon + #{coupon}, consume_coupon = consume_coupon - #{coupon} </if> " +
            "<if test='increaseCancelTimes'>, cancel_times = cancel_times + 1 </if> " +
            "WHERE id = #{id}" +
            "</script>")
    int processReturnAsset(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("coupon") BigDecimal coupon, @Param("increaseCancelTimes") boolean increaseCancelTimes);
}
