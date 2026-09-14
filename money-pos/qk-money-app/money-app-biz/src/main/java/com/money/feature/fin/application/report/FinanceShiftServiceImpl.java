package com.money.feature.fin.application.report;

import com.money.constant.BizErrorStatus; // 🌟 引入全局标准错误码
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.feature.fin.application.report.FinanceShiftService;
import com.money.util.MoneyUtil;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceShiftServiceImpl implements FinanceShiftService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;

    private BigDecimal parseAmt(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            log.error("💥 交接班金额解析异常! 出现疑似脏数据: [{}]", val, e);
            // 🌟 核心规范化：消灭魔术字符串，采用全局统一的枚举进行熔断报警
            throw new BaseException(BizErrorStatus.POS_PAYMENT_INVALID, "财务报表解析熔断：非法金额格式 [" + val + "]");
        }
    }

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

        // 🌟 这里的底层 SQL 请务必确认使用了 SUM(net_amount) AS netAmount
        List<Map<String, Object>> payStats = omsOrderPayMapper.getShiftPayStats(shiftStart, now, cashier);

        BigDecimal cashPay = BigDecimal.ZERO;
        BigDecimal scanPay = BigDecimal.ZERO;
        BigDecimal balancePay = BigDecimal.ZERO;

        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (Map<String, Object> stat : payStats) {
            String method = (String) stat.get("methodCode");
            BigDecimal amt = parseAmt(stat.get("netAmount"));

            if ("CASH".equals(method)) {
                cashPay = MoneyUtil.add(cashPay, amt);
            } else if ("BALANCE".equals(method)) {
                balancePay = MoneyUtil.add(balancePay, amt);
            } else {
                scanPay = MoneyUtil.add(scanPay, amt);

                String tag = (String) stat.get("payTag");
                if (tag == null) tag = (String) stat.get("pay_tag");

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

        Map<String, Object> discountStats = omsOrderMapper.getShiftDiscountStats(shiftStart, now, cashier);
        BigDecimal refundAmount = BigDecimal.ZERO;
        if (discountStats != null) {
            vo.setManualDiscount(parseAmt(discountStats.get("manualDiscount")));
            vo.setVoucherDiscount(parseAmt(discountStats.get("voucherDiscount")));
            vo.setMemberCouponPay(parseAmt(discountStats.get("memberCouponPay")));
            vo.setWaivedCouponAmount(parseAmt(discountStats.get("waivedCouponAmount")));
            vo.setVoucherCount(parseAmt(discountStats.get("voucherCount")).intValue());
            refundAmount = parseAmt(discountStats.get("refundAmount"));
        } else {
            vo.setManualDiscount(BigDecimal.ZERO);
            vo.setVoucherDiscount(BigDecimal.ZERO);
            vo.setMemberCouponPay(BigDecimal.ZERO);
            vo.setWaivedCouponAmount(BigDecimal.ZERO);
            vo.setVoucherCount(0);
        }
        vo.setRefundAmount(refundAmount);

        BigDecimal grossTotal = cashPay.add(scanPay).add(balancePay);
        vo.setNetIncome(grossTotal.subtract(refundAmount));

        // 由于 cashPay 是通过 netAmount 计算的（已经扣除了找零），这里就是钱箱里应该有的真实进账！
        vo.setExpectedTotalIncome(MoneyUtil.add(cashPay, scanPay));

        List<BrandContributionVO> brandMatrix = omsOrderDetailMapper.getShiftBrandContribution(shiftStart, now, cashier);
        vo.setBrandMatrix(brandMatrix);

        return vo;
    }
}