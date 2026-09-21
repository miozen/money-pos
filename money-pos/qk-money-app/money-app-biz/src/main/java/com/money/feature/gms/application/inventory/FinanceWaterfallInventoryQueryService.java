package com.money.feature.gms.application.inventory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.contract.goods.FinanceWaterfallInventoryQuery;
import com.money.contract.goods.FinanceWaterfallProcurementSnapshot;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.mapper.GmsInventoryDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** GMS implementation of the established daily inbound-procurement aggregation. */
@Service
@RequiredArgsConstructor
class FinanceWaterfallInventoryQueryService implements FinanceWaterfallInventoryQuery {
    private final GmsInventoryDocMapper inventoryDocMapper;

    @Override
    public List<FinanceWaterfallProcurementSnapshot> listDailyInboundProcurements(LocalDateTime startInclusive,
                                                                                    LocalDateTime endInclusive) {
        QueryWrapper<GmsInventoryDoc> query = new QueryWrapper<GmsInventoryDoc>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr",
                        "SUM(IFNULL(total_amount, 0)) AS procurementAmount")
                .eq("doc_type", "INBOUND")
                .groupBy("DATE(create_time)")
                .orderByDesc("DATE(create_time)");
        if (startInclusive != null) query.ge("create_time", startInclusive);
        if (endInclusive != null) query.le("create_time", endInclusive);
        return inventoryDocMapper.selectMaps(query).stream()
                .map(row -> new FinanceWaterfallProcurementSnapshot(string(row.get("dateStr")),
                        amount(row.get("procurementAmount"))))
                .collect(Collectors.toList());
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private BigDecimal amount(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
}
