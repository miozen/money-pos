package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * <p>
 * 商品表 Mapper 接口
 * </p>
 */
public interface GmsGoodsMapper extends BaseMapper<GmsGoods> {

    /**
     * 🌟 原子扣减库存 (CAS 保证不超卖)
     * WHERE stock >= #{qty} 确保库存不会减成负数（除非业务允许负库存，此处为严谨逻辑）
     */
    @Update("UPDATE gms_goods SET stock = stock - #{qty} WHERE id = #{id} AND stock >= #{qty}")
    int deductStockAtomically(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /**
     * 🌟 原子回补库存 (用于退货/取消订单)
     * 无需额外条件，直接在数据库层执行原子加法，防止并发覆盖
     */
    @Update("UPDATE gms_goods SET stock = stock + #{qty} WHERE id = #{id}")
    int addStockAtomically(@Param("id") Long id, @Param("qty") BigDecimal qty);

}
