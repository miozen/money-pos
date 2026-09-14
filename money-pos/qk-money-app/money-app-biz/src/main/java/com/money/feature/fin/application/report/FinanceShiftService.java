package com.money.feature.fin.application.report;

import com.money.dto.Finance.FinanceDataVO.*;

public interface FinanceShiftService {
    ShiftHandoverVO getShiftHandover(String startTime, String cashierName);
}