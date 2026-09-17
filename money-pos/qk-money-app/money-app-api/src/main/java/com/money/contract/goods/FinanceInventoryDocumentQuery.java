package com.money.contract.goods;

import java.time.LocalDate;
import java.util.List;

/** Read-only financial inventory-document input owned by GMS. */
public interface FinanceInventoryDocumentQuery {

    /**
     * Returns the legacy financial document types for the date's closed day range.
     * FIN decides how a negative amount contributes to its gross-profit presentation.
     */
    List<FinanceInventoryDocumentSnapshot> listDailyFinancialDocuments(LocalDate date);
}
