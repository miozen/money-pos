package com.money.feature.home.application;

import com.money.contract.goods.InventoryValuationQuery;
import com.money.contract.member.HomeDailyMemberQuery;
import com.money.contract.trade.HomeDailyOrderSnapshot;
import com.money.contract.trade.HomeOrderReadQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Assembles HOME's existing daily snapshot formula without writing it. */
@Component
@RequiredArgsConstructor
public class HomeDailySnapshotAssembler {
    private final InventoryValuationQuery inventoryValuationQuery;
    private final HomeOrderReadQuery homeOrderReadQuery;
    private final HomeDailyMemberQuery homeDailyMemberQuery;

    public HomeDailySnapshotValues assemble(LocalDate date) {
        HomeDailyOrderSnapshot orderSnapshot = homeOrderReadQuery.summarizeDailySnapshot(date);
        BigDecimal salesAmount = orderSnapshot.getSalesAmount();
        BigDecimal profitAmount = salesAmount.subtract(orderSnapshot.getCostAmount());
        int orderCount = orderSnapshot.getOrderCount();
        BigDecimal inventoryValue = inventoryValuationQuery.getCurrentStockValue();
        return new HomeDailySnapshotValues(date, salesAmount, orderCount, profitAmount,
                orderCount > 0 ? salesAmount.divide(new BigDecimal(orderCount), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO,
                inventoryValue != null ? inventoryValue : BigDecimal.ZERO,
                homeDailyMemberQuery.countNewMembers(date));
    }
}
