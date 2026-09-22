package com.money.constant;

/**
 * 🌟 财务真理模具 (V6.0 荣耀版)
 * 作用：全局唯一公式定义，所有 Mapper 必须引用此处的逻辑，严禁私自手写
 */
public class FinancialMetric {

    // 1. 【状态红线】：经营有效状态集 (MyBatis 脚本片段)
    public static final String VALID_STATUS_SQL = "'PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'RETURN'";

    // 2. 【金额红线】：净销售额真理公式 (Net Sales)
    // 逻辑：优先取 final_sales_amount，如果为空则取实付金额
    public static final String NET_SALES_FORMULA = "IFNULL(final_sales_amount, pay_amount)";

    // 3. 【退款红线 - 财务轨】：实退钱公式 (Refund Cash)
    public static final String REFUND_CASH_FORMULA = "(pay_amount - IFNULL(final_sales_amount, pay_amount))";

    // 4. 【退款红线 - 业务轨】：退回货量公式 (Refund Qty)
    public static final String VALID_GOODS_QTY_FORMULA = "(quantity - IFNULL(return_quantity, 0))";

    // 5. 【利润红线】：净毛利公式 (Profit)
    // 逻辑：净销售额 - 成本快照
    public static final String NET_PROFIT_FORMULA = "(" + NET_SALES_FORMULA + " - IFNULL(cost_amount, 0))";
}
