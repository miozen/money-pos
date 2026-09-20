package com.money.feature.home.application;

import com.money.feature.home.infrastructure.persistence.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes short, HOME-owned atomic writes after snapshot values have been assembled. */
@Service
@RequiredArgsConstructor
public class HomeDailySummaryWriter {
    private final OmsDailySummaryMapper dailySummaryMapper;

    @Transactional
    public void insertIfAbsent(HomeDailySnapshotValues values) {
        dailySummaryMapper.insertIfAbsent(toEntity(values));
    }

    @Transactional
    public void refresh(HomeDailySnapshotValues values) {
        dailySummaryMapper.upsertSnapshot(toEntity(values));
    }

    private OmsDailySummary toEntity(HomeDailySnapshotValues values) {
        OmsDailySummary summary = new OmsDailySummary();
        summary.setRecordDate(values.getRecordDate());
        summary.setSalesAmount(values.getSalesAmount());
        summary.setOrderCount(values.getOrderCount());
        summary.setProfitAmount(values.getProfitAmount());
        summary.setAsp(values.getAsp());
        summary.setInventoryValue(values.getInventoryValue());
        summary.setNewMemberCount(values.getNewMemberCount());
        return summary;
    }
}
