package com.money.feature.gms.application.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.InventoryDocTypeEnum;
import com.money.contract.goods.FinanceInventoryDocumentQuery;
import com.money.contract.goods.FinanceInventoryDocumentSnapshot;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.mapper.GmsInventoryDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** GMS-owned financial inventory-document projection; FIN receives no persistence entity. */
@Service
@RequiredArgsConstructor
class FinanceInventoryDocumentQueryService implements FinanceInventoryDocumentQuery {

    private final GmsInventoryDocMapper inventoryDocMapper;

    @Override
    public List<FinanceInventoryDocumentSnapshot> listDailyFinancialDocuments(LocalDate date) {
        LocalDateTime startOfDay = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(date, LocalTime.MAX);
        return inventoryDocMapper.selectList(new LambdaQueryWrapper<GmsInventoryDoc>()
                        .select(GmsInventoryDoc::getDocType, GmsInventoryDoc::getTotalAmount)
                        .ge(GmsInventoryDoc::getCreateTime, startOfDay)
                        .le(GmsInventoryDoc::getCreateTime, endOfDay)
                        .in(GmsInventoryDoc::getDocType,
                                InventoryDocTypeEnum.OUTBOUND.name(), InventoryDocTypeEnum.CHECK.name()))
                .stream()
                .map(doc -> new FinanceInventoryDocumentSnapshot(doc.getDocType(), doc.getTotalAmount()))
                .toList();
    }
}
