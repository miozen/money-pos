package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCombo;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 领域服务：商品库存高敏子域 (Domain Service)
 * 职责：统一管控后台入库、出库、报损的库存变更，与前台收银口径保持绝对一致
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmsGoodsStockService {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;

    /**
     * 核心变更：后台手工增减库存 (严密防线的最终形态)
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStock(Long goodsId, Integer qty) {
        if (goodsId == null || qty == null || qty == 0) return;

        GmsGoods goods = gmsGoodsMapper.selectById(goodsId);
        if (goods == null) {
            throw new BaseException("【库存强控拦截】商品档案不存在，ID：" + goodsId);
        }

        // 1. 先原子化更新主商品（或套餐配额）的库存
        executeStrictStockUpdate(goods, qty);

        // 2. 如果是套餐，必须穿透更新单品
        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(
                    new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, goodsId)
            );

            if (combos == null || combos.isEmpty()) {
                throw new BaseException("【库存强控拦截】套餐商品未配置子明细，无法扣减真实库存: " + goods.getName());
            }

            for (GmsGoodsCombo combo : combos) {
                if (combo.getSubGoodsId() != null) {
                    GmsGoods subGoods = gmsGoodsMapper.selectById(combo.getSubGoodsId());
                    if (subGoods == null) {
                        throw new BaseException("【库存强控拦截】套餐内子商品档案不存在，ID：" + combo.getSubGoodsId());
                    }

                    // 🌟 终极打补丁 1：严防脏数据！子项配方数量必须 > 0，杜绝出现乘以 0 或负数的“库存黑洞”
                    Integer subQty = combo.getSubGoodsQty();
                    if (subQty == null || subQty <= 0) {
                        throw new BaseException(String.format("【库存强控拦截】套餐「%s」配置的子商品「%s」数量异常(必须大于0)！", goods.getName(), subGoods.getName()));
                    }

                    int addQty;
                    try {
                        addQty = Math.multiplyExact(qty, subQty);
                    } catch (ArithmeticException e) {
                        throw new BaseException(String.format("【库存强控拦截】套餐子商品「%s」变动总数超出系统安全上限", subGoods.getName()));
                    }

                    // 穿透执行严格库存更新
                    executeStrictStockUpdate(subGoods, addQty);
                }
            }
        }
    }

    /**
     * 底层抽离：严格交易级原子库存更新 (自带防超扣底线)
     */
    private void executeStrictStockUpdate(GmsGoods targetGoods, int changeQty) {
        LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<GmsGoods>()
                .eq(GmsGoods::getId, targetGoods.getId());

        if (changeQty < 0) {
            // 扣减操作：必须确保扣减后不为负数
            int absQty = Math.abs(changeQty);
            updateWrapper.apply("IFNULL(stock, 0) >= {0}", absQty)
                    .setSql("stock = IFNULL(stock, 0) - " + absQty);

            int rows = gmsGoodsMapper.update(null, updateWrapper);
            if (rows == 0) {
                throw new BaseException(String.format("【库存强控拦截】商品「%s」当前账面库存不足，拒绝扣成负数！(试图扣减: %d)", targetGoods.getName(), absQty));
            }
        } else {
            // 增加操作：直接累加
            updateWrapper.setSql("stock = IFNULL(stock, 0) + " + changeQty);
            int rows = gmsGoodsMapper.update(null, updateWrapper);
            // 🌟 终极打补丁 2：增加库存也执行 rows 校验，防止并发时由于商品被物理删除导致的静默失败
            if (rows == 0) {
                throw new BaseException(String.format("【库存强控拦截】商品「%s」增加库存失败，底层数据可能已失效！", targetGoods.getName()));
            }
        }
    }

    /**
     * 核心统计：计算全盘实时库存总货值
     */
    public BigDecimal getCurrentStockValue() {
        try {
            QueryWrapper<GmsGoods> wrapper = new QueryWrapper<>();
            wrapper.select("IFNULL(SUM(stock * purchase_price), 0) AS totalValue").gt("stock", 0);
            Map<String, Object> map = gmsGoodsMapper.selectMaps(wrapper).stream().findFirst().orElse(null);
            if (map != null && map.get("totalValue") != null) {
                return new BigDecimal(map.get("totalValue").toString());
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("💥 库存总货值计算异常: ", e);
            throw new BaseException("库存盘点数据严重异常：无法计算实时大盘货值，为防止账目错乱，请联系技术人员排查底层数据！");
        }
    }
}
