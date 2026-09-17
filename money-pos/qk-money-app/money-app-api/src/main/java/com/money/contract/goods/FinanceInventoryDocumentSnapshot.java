package com.money.contract.goods;

import java.math.BigDecimal;

/** Minimal GMS financial-document projection for FIN dashboard assembly. */
public class FinanceInventoryDocumentSnapshot {

    private final String documentType;
    private final BigDecimal totalAmount;

    public FinanceInventoryDocumentSnapshot(String documentType, BigDecimal totalAmount) {
        this.documentType = documentType;
        this.totalAmount = totalAmount;
    }

    public String getDocumentType() {
        return documentType;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
