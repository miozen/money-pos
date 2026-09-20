package com.money.feature.gms.application.inventory;

import com.baomidou.mybatisplus.extension.service.IService;
import com.money.dto.inventory.GmsInventoryOrderDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryOrder;

public interface GmsInventoryOrderService extends IService<GmsInventoryOrder> {

    /**
     * 创建采购入库单 (核心业务)
     */
    void createInboundOrder(GmsInventoryOrderDTO dto);

    /**
     * 创建库存盘点单 (直接覆盖真实库存)
     */
    void createCheckOrder(GmsInventoryOrderDTO dto);

    /**
     * 创建报损出库单 (扣减库存，计算损耗成本)
     */
    void createOutboundOrder(GmsInventoryOrderDTO dto);
}
