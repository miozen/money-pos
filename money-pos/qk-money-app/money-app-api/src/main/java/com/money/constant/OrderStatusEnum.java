package com.money.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 🌟 金融级订单状态机枚举 (带动态兜底翻译功能)
 */
public enum OrderStatusEnum {

    /** 待支付 */
    UNPAID("待支付"),

    /** 已支付 (正常结算完成) */
    PAID("已支付"),

    /** 部分退款 (发生过部分退货，但还能继续退) */
    PARTIAL_REFUNDED("部分退货"),

    /** 全额退款 (终态，不可再操作) */
    REFUNDED("已退单"), // 🌟 这里的兜底文案已对齐您的字典

    /** 已取消 (未支付直接作废的订单) */
    CLOSED("已取消");

    /** Historical full-refund code. It is readable but never written by current workflows. */
    public static final String LEGACY_RETURN = "RETURN";

    private final String desc;

    OrderStatusEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 🌟 兜底翻译器：为 VO 提供基础状态映射
     */
    public static String getFallbackDesc(String statusCode) {
        if (statusCode == null) return "-";
        if (LEGACY_RETURN.equals(statusCode)) return REFUNDED.getDesc();
        for (OrderStatusEnum status : values()) {
            if (status.name().equals(statusCode)) {
                return status.getDesc();
            }
        }
        return statusCode; // 未知状态原样输出
    }

    /**
     * 🌟 核心引擎：定义“经营有效集”
     */
    public static List<String> getValidFinancialStatus() {
        return Arrays.asList(PAID.name(), PARTIAL_REFUNDED.name(), REFUNDED.name(), LEGACY_RETURN);
    }
}
