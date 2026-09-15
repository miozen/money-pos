package com.money.feature.gms.application.inventory;

import com.money.dto.GmsGoods.InventoryDocRequestDTO;

public interface GmsInventoryDocService {

    /**
     * 🌟 大一统库存单据执行引擎
     * 自动分发：入库(计算均价)、报损(资产扣减)、盘点(多退少补)
     */
    void executeDoc(InventoryDocRequestDTO requestDTO);
}
