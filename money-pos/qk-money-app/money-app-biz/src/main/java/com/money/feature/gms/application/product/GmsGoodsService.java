package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.extension.service.IService;
import com.money.dto.GmsGoods.GmsGoodsDTO;
import com.money.dto.GmsGoods.GmsGoodsQueryDTO;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.entity.GmsGoods;
import com.money.web.vo.PageVO;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 商品核心服务接口 (已纯净瘦身版)
 * 职责：专注商品及库存的基础管理
 */
public interface GmsGoodsService extends IService<GmsGoods> {

    PageVO<GmsGoodsVO> list(GmsGoodsQueryDTO queryDTO);

    void add(GmsGoodsDTO addDTO, MultipartFile pic);

    void update(GmsGoodsDTO updateDTO, MultipartFile pic);

    void delete(Set<Long> ids);

    void sell(Long goodsId, Integer qty);

    void updateStock(Long goodsId, Integer qty);

    BigDecimal getCurrentStockValue();

    // 🌟 瘦身成功：importGoods 接口定义已被彻底移除！
}
