package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PosSkuLevelPriceMapper extends BaseMapper<PosSkuLevelPrice> {
}
