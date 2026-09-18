package com.money.feature.trade.application.orderquery;

import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.web.vo.PageVO;

/**
 * TRADE-owned compatibility read service for the legacy OMS profit-audit endpoint.
 */
public interface OmsOrderProfitAuditQueryService {

    PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO);
}
