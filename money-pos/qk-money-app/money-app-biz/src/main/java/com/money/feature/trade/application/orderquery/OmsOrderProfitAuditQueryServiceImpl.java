package com.money.feature.trade.application.orderquery;

import com.money.contract.trade.FinanceProfitAuditPageSnapshot;
import com.money.contract.trade.FinanceProfitAuditQuery;
import com.money.contract.trade.FinanceProfitAuditSnapshot;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Preserves the legacy OMS profit-audit response while keeping its read path in TRADE.
 */
@Service
@RequiredArgsConstructor
public class OmsOrderProfitAuditQueryServiceImpl implements OmsOrderProfitAuditQueryService {

    private final FinanceProfitAuditQuery financeProfitAuditQuery;

    @Override
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        FinanceProfitAuditPageSnapshot page = financeProfitAuditQuery.getProfitAuditPage(
                queryDTO.getPage(), queryDTO.getSize(), queryDTO.getOrderNo(), queryDTO.getStatus());
        List<ProfitAuditVO> records = new ArrayList<>();
        for (FinanceProfitAuditSnapshot row : page.getRecords()) {
            ProfitAuditVO vo = new ProfitAuditVO();
            vo.setOrderNo(row.getOrderNo());
            vo.setGoodsName(row.getGoodsName());
            vo.setCreateTime(row.getCreateTime());
            vo.setSalePrice(row.getSalePrice());
            vo.setGoodsPrice(row.getGoodsPrice());
            vo.setPurchasePrice(row.getPurchasePrice());
            vo.setUnitProfit(row.getUnitProfit());
            vo.setProfitMargin(row.getProfitMargin());
            vo.setIsMissingCost(row.getMissingCost());
            records.add(vo);
        }
        return new PageVO<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }
}
