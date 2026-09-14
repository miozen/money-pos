package com.money.feature.trade.application.boundary.facade;

import com.money.dto.pos.PricingResult;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.feature.trade.application.support.PosCalculationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** POS pricing boundary. */
@Service
@RequiredArgsConstructor
public class PosPricingFacade {
    private final PosCalculationEngine posCalculationEngine;

    public PricingResult trial(SettleTrialReqDTO request) {
        return posCalculationEngine.calculate(request);
    }
}
