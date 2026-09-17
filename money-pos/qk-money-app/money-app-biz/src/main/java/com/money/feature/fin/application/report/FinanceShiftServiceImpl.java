package com.money.feature.fin.application.report;

import com.money.contract.goods.BrandNameQuery;
import com.money.contract.trade.FinanceShiftBrandContributionSnapshot;
import com.money.contract.trade.FinanceShiftDiscountSnapshot;
import com.money.contract.trade.FinanceShiftHandoverQuery;
import com.money.contract.trade.FinanceShiftPaymentSnapshot;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class FinanceShiftServiceImpl implements FinanceShiftService {

    private final FinanceShiftHandoverQuery financeShiftHandoverQuery;
    private final BrandNameQuery brandNameQuery;

    @Override
    public ShiftHandoverVO getShiftHandover(String startTime, String cashierName) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime shiftStart = LocalDateTime.parse(startTime, dtf);
        LocalDateTime now = LocalDateTime.now();
        String cashier = (cashierName != null && !cashierName.trim().isEmpty()) ? cashierName : "全部收银员";

        ShiftHandoverVO vo = new ShiftHandoverVO();
        vo.setShiftStartTime(startTime);
        vo.setShiftEndTime(now.format(dtf));
        vo.setCashierName(cashier);

        List<FinanceShiftPaymentSnapshot> payStats = financeShiftHandoverQuery
                .listPaymentSummaries(shiftStart, now, cashier);

        BigDecimal cashPay = BigDecimal.ZERO;
        BigDecimal scanPay = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (FinanceShiftPaymentSnapshot stat : payStats) {
            String method = stat.getMethodCode();
            BigDecimal amt = zero(stat.getNetAmount());

            if ("CASH".equals(method)) {
                cashPay = MoneyUtil.add(cashPay, amt);
            } else if ("BALANCE".equals(method)) {
                balancePay = MoneyUtil.add(balancePay, amt);
            } else {
                scanPay = MoneyUtil.add(scanPay, amt);

                String tag = stat.getPayTag();

                tag = (tag != null && !tag.trim().isEmpty()) ? tag : "UNKNOWN";
                scanTagMap.put(tag, scanTagMap.getOrDefault(tag, BigDecimal.ZERO).add(amt));
            }
        }
        vo.setCashPay(cashPay);
        vo.setScanPay(scanPay);
        vo.setBalancePay(balancePay);

        List<PayPieData> scanBreakdownList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                scanBreakdownList.add(new PayPieData(entry.getKey(), entry.getValue()));
            }
        }
        vo.setScanPayBreakdown(scanBreakdownList);

        FinanceShiftDiscountSnapshot discountStats = financeShiftHandoverQuery
                .getDiscountSummary(shiftStart, now, cashier);
        vo.setManualDiscount(zero(discountStats.getManualDiscount()));
        vo.setVoucherDiscount(zero(discountStats.getVoucherDiscount()));
        vo.setMemberCouponPay(zero(discountStats.getMemberCouponPay()));
        vo.setWaivedCouponAmount(zero(discountStats.getWaivedCouponAmount()));
        vo.setVoucherCount((int) discountStats.getVoucherCount());
        BigDecimal refundAmount = zero(discountStats.getRefundAmount());
        vo.setRefundAmount(refundAmount);

        BigDecimal grossTotal = cashPay.add(scanPay).add(balancePay);
        vo.setNetIncome(grossTotal.subtract(refundAmount));

        // 由于 cashPay 是通过 netAmount 计算的（已经扣除了找零），这里就是钱箱里应该有的真实进账！
        vo.setExpectedTotalIncome(MoneyUtil.add(cashPay, scanPay));

        List<FinanceShiftBrandContributionSnapshot> contributions = financeShiftHandoverQuery
                .listBrandContributions(shiftStart, now, cashier);
        java.util.Set<String> brandIds = new HashSet<>();
        for (FinanceShiftBrandContributionSnapshot contribution : contributions) {
            if (contribution.getBrandId() != null) brandIds.add(contribution.getBrandId());
        }
        Map<String, String> brandNames = brandNameQuery.findNamesByIds(brandIds);
        List<BrandContributionVO> brandMatrix = new ArrayList<>();
        for (FinanceShiftBrandContributionSnapshot contribution : contributions) {
            String brandName = contribution.getBrandId() == null ? null : brandNames.get(contribution.getBrandId());
            brandMatrix.add(new BrandContributionVO(brandName == null ? "无品牌/未知" : brandName,
                    zero(contribution.getRevenue()), zero(contribution.getCouponConsumption())));
        }
        vo.setBrandMatrix(brandMatrix);

        return vo;
    }

    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
