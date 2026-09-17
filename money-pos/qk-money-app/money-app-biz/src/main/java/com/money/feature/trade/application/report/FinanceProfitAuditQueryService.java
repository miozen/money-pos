package com.money.feature.trade.application.report;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.contract.trade.FinanceProfitAuditPageSnapshot;
import com.money.contract.trade.FinanceProfitAuditQuery;
import com.money.contract.trade.FinanceProfitAuditSnapshot;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.mapper.OmsOrderAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** TRADE implementation of the established paged FIN profit-audit projection. */
@Service
@RequiredArgsConstructor
class FinanceProfitAuditQueryService implements FinanceProfitAuditQuery {
    private final OmsOrderAuditMapper orderAuditMapper;

    @Override
    public FinanceProfitAuditPageSnapshot getProfitAuditPage(long page, long size, String orderNo, String status) {
        Page<ProfitAuditVO> result = orderAuditMapper.getProfitAuditPage(new Page<ProfitAuditVO>(page, size),
                orderNo, status);
        List<FinanceProfitAuditSnapshot> records = result.getRecords().stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());
        return new FinanceProfitAuditPageSnapshot(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    private FinanceProfitAuditSnapshot toSnapshot(ProfitAuditVO row) {
        return new FinanceProfitAuditSnapshot(row.getOrderNo(), row.getGoodsName(), row.getCreateTime(),
                row.getSalePrice(), row.getGoodsPrice(), row.getPurchasePrice(), row.getUnitProfit(),
                row.getProfitMargin(), row.getIsMissingCost() == null ? 0 : row.getIsMissingCost());
    }
}
