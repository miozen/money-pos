package com.money.feature.gms.application.turnover;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.TurnoverDataVO.TurnoverDashboardVO;
import com.money.dto.GmsGoods.TurnoverDataVO.WarningItemVO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsTurnoverWarningSnapshot;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsTurnoverWarningSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Persists today's GMS turnover snapshot without granting HTTP queries write authority. */
@Service
@RequiredArgsConstructor
public class GmsTurnoverSnapshotCommandServiceImpl implements GmsTurnoverSnapshotCommandService {

    private final GmsTurnoverService turnoverService;
    private final GmsTurnoverWarningSnapshotMapper snapshotMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshTodaySnapshot() {
        TurnoverDashboardVO dashboard = turnoverService.getTurnoverWarnings();
        LocalDate today = LocalDate.now();
        GmsTurnoverWarningSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<GmsTurnoverWarningSnapshot>()
                        .eq(GmsTurnoverWarningSnapshot::getSnapshotDate, today));
        if (snapshot == null) {
            snapshot = new GmsTurnoverWarningSnapshot();
            snapshot.setSnapshotDate(today);
        }

        List<WarningItemVO> replenishList = dashboard.getReplenishList();
        List<WarningItemVO> deadStockList = dashboard.getDeadStockList();
        snapshot.setReplenishCount(replenishList.size());
        snapshot.setDeadStockCount(deadStockList.size());
        snapshot.setTopReplenishGoodsJson(JSONUtil.toJsonStr(replenishList.subList(0, Math.min(20, replenishList.size()))));
        snapshot.setTopDeadStockGoodsJson(JSONUtil.toJsonStr(deadStockList.subList(0, Math.min(20, deadStockList.size()))));

        if (snapshot.getId() == null) {
            snapshotMapper.insert(snapshot);
        } else {
            snapshotMapper.updateById(snapshot);
        }
    }
}
