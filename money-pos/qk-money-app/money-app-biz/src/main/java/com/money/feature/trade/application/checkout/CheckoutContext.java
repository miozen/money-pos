package com.money.feature.trade.application.checkout;

import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.PricingResult; // 🌟 引入新契约
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO;
import com.money.contract.member.MemberCheckoutSnapshot;
import com.money.entity.GmsGoods;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 🌟 结算流水线公文包 (Context)
 */
@Data
public class CheckoutContext {

    // 1. 顾客的原始请求
    private SettleAccountsDTO request;

    // 2. 安检员核实后的数据
    private MemberCheckoutSnapshot member;
    private Map<Long, GmsGoods> goodsMap;

    // 3. 精算师和出纳员算好的数据
    private PricingResult pricingResult; // 🌟 核心替换：使用真理结果对象
    private NormalizedPaymentResult paymentResult;

    // 4. 档案员建好的实体
    private OmsOrder order;
    private List<OmsOrderDetail> orderDetails;

    // 5. 最终要返回给前端的小票结果
    private SettleResultVO finalResult;
}
