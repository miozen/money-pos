package com.money.contract.trade;

/** TRADE-owned paged profit-audit projection for FIN reporting. */
public interface FinanceProfitAuditQuery {

    FinanceProfitAuditPageSnapshot getProfitAuditPage(long page, long size, String orderNo, String status);
}
