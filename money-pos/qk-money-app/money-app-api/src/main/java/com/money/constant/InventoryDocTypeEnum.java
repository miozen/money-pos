package com.money.constant;

public enum InventoryDocTypeEnum {
    INBOUND("采购入库"),
    OUTBOUND("报损出库"),
    CHECK("盘点对冲"),
    SALE_OUT("销售出库"),
    MEMBER_PICKUP("会员提货出库"),
    MEMBER_PICKUP_RETURN("会员提货退货入库");

    private final String desc;

    InventoryDocTypeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
