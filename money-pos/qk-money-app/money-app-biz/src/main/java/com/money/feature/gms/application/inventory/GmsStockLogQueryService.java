package com.money.feature.gms.application.inventory;

import com.money.dto.GmsGoods.GmsStockLogQueryDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.web.vo.PageVO;

/**
 * GMS 库存流水的只读查询边界。
 */
public interface GmsStockLogQueryService {

    PageVO<GmsStockLog> list(GmsStockLogQueryDTO queryDTO, String goodsBarcode);
}
