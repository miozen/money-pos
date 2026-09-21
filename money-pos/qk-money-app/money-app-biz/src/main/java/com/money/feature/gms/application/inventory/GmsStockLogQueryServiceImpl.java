package com.money.feature.gms.application.inventory;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.dto.GmsGoods.GmsStockLogQueryDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.mapper.GmsStockLogMapper;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmsStockLogQueryServiceImpl implements GmsStockLogQueryService {

    private final GmsStockLogMapper gmsStockLogMapper;

    @Override
    public PageVO<GmsStockLog> list(GmsStockLogQueryDTO queryDTO, String goodsBarcode) {
        Page<GmsStockLog> page = gmsStockLogMapper.selectPage(
                PageUtil.toPage(queryDTO),
                new LambdaQueryWrapper<GmsStockLog>()
                        .eq(StrUtil.isNotBlank(goodsBarcode), GmsStockLog::getGoodsBarcode, goodsBarcode)
                        .like(StrUtil.isNotBlank(queryDTO.getGoodsName()), GmsStockLog::getGoodsName, queryDTO.getGoodsName())
                        .eq(StrUtil.isNotBlank(queryDTO.getType()), GmsStockLog::getType, queryDTO.getType())
                        .like(StrUtil.isNotBlank(queryDTO.getOrderNo()), GmsStockLog::getOrderNo, queryDTO.getOrderNo())
                        .orderByDesc(GmsStockLog::getCreateTime)
        );
        return PageUtil.toPageVO(page, GmsStockLog::new);
    }
}
