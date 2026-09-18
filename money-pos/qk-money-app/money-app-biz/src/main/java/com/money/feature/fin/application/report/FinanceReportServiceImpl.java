package com.money.feature.fin.application.report;

import com.money.dto.Finance.FinanceWaterfallQueryDTO;
import com.money.dto.Finance.FinanceWaterfallVO;
import com.money.contract.goods.FinanceWaterfallInventoryQuery;
import com.money.contract.goods.FinanceWaterfallProcurementSnapshot;
import com.money.contract.trade.FinanceWaterfallOrderQuery;
import com.money.contract.trade.FinanceWaterfallOrderSnapshot;
import com.money.feature.fin.application.report.FinanceReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * <p>
 * 财务瀑布流报表 服务实现类 (计算下沉防御版)
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceReportServiceImpl implements FinanceReportService {

    private final FinanceWaterfallOrderQuery financeWaterfallOrderQuery;
    private final FinanceWaterfallInventoryQuery financeWaterfallInventoryQuery;

    @Override
    public List<FinanceWaterfallVO> getDailyWaterfallReport(FinanceWaterfallQueryDTO queryDTO) {
        // 🌟 防御性降级：空指针拦截！
        // 严禁未初始化的查询对象打穿到持久层，防止 NPE 或引发无边界的全局聚合扫描
        if (queryDTO == null) {
            log.warn("财务瀑布流查询参数 queryDTO 为空，已执行短路拦截");
            return Collections.emptyList();
        }

        Map<String, FinanceWaterfallVO> rowsByDate = new TreeMap<>(Collections.reverseOrder());
        for (FinanceWaterfallOrderSnapshot snapshot : financeWaterfallOrderQuery
                .listDailyWaterfallOrders(queryDTO.getStartTime(), queryDTO.getEndTime())) {
            FinanceWaterfallVO row = rowFor(rowsByDate, snapshot.getDate());
            row.setTotalAmount(snapshot.getTotalAmount());
            row.setCouponAmount(snapshot.getCouponAmount());
            row.setVoucherAmount(snapshot.getVoucherAmount());
            row.setManualDiscountAmount(snapshot.getManualDiscountAmount());
            row.setPayAmount(snapshot.getPayAmount());
            row.setRefundAmount(snapshot.getRefundAmount());
            row.setNetIncome(snapshot.getNetIncome());
        }
        for (FinanceWaterfallProcurementSnapshot snapshot : financeWaterfallInventoryQuery
                .listDailyInboundProcurements(queryDTO.getStartTime(), queryDTO.getEndTime())) {
            rowFor(rowsByDate, snapshot.getDate()).setProcurementAmount(snapshot.getProcurementAmount());
        }
        return new ArrayList<>(rowsByDate.values());
    }

    private FinanceWaterfallVO rowFor(Map<String, FinanceWaterfallVO> rowsByDate, String date) {
        FinanceWaterfallVO row = rowsByDate.get(date);
        if (row != null) return row;
        row = new FinanceWaterfallVO();
        row.setDate(date);
        row.setTotalAmount(java.math.BigDecimal.ZERO);
        row.setCouponAmount(java.math.BigDecimal.ZERO);
        row.setVoucherAmount(java.math.BigDecimal.ZERO);
        row.setManualDiscountAmount(java.math.BigDecimal.ZERO);
        row.setPayAmount(java.math.BigDecimal.ZERO);
        row.setRefundAmount(java.math.BigDecimal.ZERO);
        row.setNetIncome(java.math.BigDecimal.ZERO);
        row.setProcurementAmount(java.math.BigDecimal.ZERO);
        rowsByDate.put(date, row);
        return row;
    }
}
