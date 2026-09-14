package com.money.feature.fin.application.report;

import com.money.dto.Finance.FinanceWaterfallQueryDTO;
import com.money.dto.Finance.FinanceWaterfallVO;
import com.money.feature.fin.infrastructure.persistence.mapper.FinanceReportMapper;
import com.money.feature.fin.application.report.FinanceReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

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

    private final FinanceReportMapper financeReportMapper;

    @Override
    public List<FinanceWaterfallVO> getDailyWaterfallReport(FinanceWaterfallQueryDTO queryDTO) {
        // 🌟 防御性降级：空指针拦截！
        // 严禁未初始化的查询对象打穿到持久层，防止 NPE 或引发无边界的全局聚合扫描
        if (queryDTO == null) {
            log.warn("财务瀑布流查询参数 queryDTO 为空，已执行短路拦截");
            return Collections.emptyList();
        }

        // 🌟 架构备忘：极致的“计算下沉”。
        // Java 内存开销为 0。所有期初、流入、流出、期末的统筹计算，
        // 均交由底层 FinanceReportMapper 通过 UNION ALL (oms_order_pay & ums_member_log) 完成。
        return financeReportMapper.getDailyWaterfallReport(queryDTO.getStartTime(), queryDTO.getEndTime());
    }
}