package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.contract.member.FinanceMemberAssetCompositionSnapshot;
import com.money.contract.member.FinanceMemberAssetQuery;
import com.money.contract.member.FinanceMemberRechargeSnapshot;
import com.money.contract.member.FinanceMemberRechargeTotalSnapshot;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberLog;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** UMS-owned projections that preserve FIN's established member-asset financial inputs. */
@Service
@RequiredArgsConstructor
class FinanceMemberAssetQueryService implements FinanceMemberAssetQuery {

    private static final List<String> FINANCIAL_RECHARGE_OPERATIONS = List.of("RECHARGE", "REVERSAL");

    private final UmsMemberMapper memberMapper;
    private final UmsMemberLogMapper memberLogMapper;

    @Override
    public List<FinanceMemberRechargeSnapshot> listDailyRecharges(LocalDate date) {
        return memberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                        .select(UmsMemberLog::getRealAmount)
                        .ge(UmsMemberLog::getCreateTime, startOfDay(date))
                        .le(UmsMemberLog::getCreateTime, endOfDay(date))
                        .in(UmsMemberLog::getOperateType, FINANCIAL_RECHARGE_OPERATIONS))
                .stream()
                .map(log -> new FinanceMemberRechargeSnapshot(log.getRealAmount()))
                .toList();
    }

    @Override
    public List<FinanceMemberRechargeTotalSnapshot> listDailyRechargeTotals(LocalDate startInclusive,
                                                                               LocalDate endInclusive) {
        return memberLogMapper.selectMaps(new QueryWrapper<UmsMemberLog>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(real_amount) AS totalAmt")
                        .ge("create_time", startOfDay(startInclusive))
                        .le("create_time", endOfDay(endInclusive))
                        .in("operate_type", FINANCIAL_RECHARGE_OPERATIONS)
                        .groupBy("DATE(create_time)"))
                .stream()
                .map(this::toDailyTotal)
                .toList();
    }

    @Override
    public BigDecimal getPositiveBalanceTotal() {
        return memberMapper.selectObjs(new LambdaQueryWrapper<UmsMember>()
                        .select(UmsMember::getBalance)
                        .isNotNull(UmsMember::getBalance)
                        .gt(UmsMember::getBalance, BigDecimal.ZERO))
                .stream()
                .map(value -> (BigDecimal) value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public FinanceMemberAssetCompositionSnapshot getAssetComposition() {
        Map<String, Object> composition = memberMapper.getFinanceAssetComposition();
        return new FinanceMemberAssetCompositionSnapshot(
                amount(composition.get("totalPrincipal")), amount(composition.get("totalGift")));
    }

    private FinanceMemberRechargeTotalSnapshot toDailyTotal(Map<String, Object> row) {
        return new FinanceMemberRechargeTotalSnapshot(
                LocalDate.parse(String.valueOf(row.get("dateStr"))), amount(row.get("totalAmt")));
    }

    private BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIN);
    }

    private LocalDateTime endOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }
}
