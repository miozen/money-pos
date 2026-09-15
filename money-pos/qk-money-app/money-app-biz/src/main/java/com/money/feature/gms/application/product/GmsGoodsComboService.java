package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.GmsGoodsComboDTO;
import com.money.entity.GmsGoodsCombo;
import com.money.mapper.GmsGoodsComboMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领域服务：商品套餐组合子域 (Domain Service)
 * 职责：专注处理子商品捆绑逻辑 (BOM清单)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmsGoodsComboService {

    private final GmsGoodsComboMapper gmsGoodsComboMapper;

    /**
     * 核心写入：同步覆盖套餐明细
     */
    public void saveComboDetails(Long comboGoodsId, Integer isCombo, List<GmsGoodsComboDTO> subGoodsList) {
        if (isCombo != null && isCombo == 1 && subGoodsList != null && !subGoodsList.isEmpty()) {
            // 全删全增策略，保障套餐明细的绝对一致性
            gmsGoodsComboMapper.delete(new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, comboGoodsId));
            for (GmsGoodsComboDTO sub : subGoodsList) {
                GmsGoodsCombo comboObj = new GmsGoodsCombo();
                comboObj.setComboGoodsId(comboGoodsId);
                comboObj.setSubGoodsId(sub.getSubGoodsId());
                comboObj.setSubGoodsQty(sub.getSubGoodsQty() != null ? sub.getSubGoodsQty() : 1);
                gmsGoodsComboMapper.insert(comboObj);
            }
        } else {
            gmsGoodsComboMapper.delete(new LambdaQueryWrapper<GmsGoodsCombo>().eq(GmsGoodsCombo::getComboGoodsId, comboGoodsId));
        }
    }

    /**
     * 核心读取：批量获取套餐映射网
     */
    public Map<Long, List<GmsGoodsCombo>> getComboMap(List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) return new java.util.HashMap<>();
        List<GmsGoodsCombo> allCombos = gmsGoodsComboMapper.selectList(
                new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, goodsIds)
        );
        return allCombos.stream().collect(Collectors.groupingBy(GmsGoodsCombo::getComboGoodsId));
    }

    /**
     * 核心清理：商品被删时，级联清理套餐挂载记录
     */
    public void deleteByGoodsIds(Set<Long> goodsIds) {
        if (goodsIds != null && !goodsIds.isEmpty()) {
            gmsGoodsComboMapper.delete(new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, goodsIds));
        }
    }
}
