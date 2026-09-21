package com.money.support;

import com.money.dto.OmsOrderDetail.OmsOrderDetailDTO;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class TradeFixture {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final UmsMemberMapper umsMemberMapper;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    public GmsGoods createSellableGoods(String suffix, long stock, BigDecimal salePrice) {
        GmsGoods goods = new GmsGoods();
        goods.setBarcode("TEST-" + suffix);
        goods.setName("Test goods " + suffix);
        goods.setPinyin("test");
        goods.setPic("");
        goods.setUnit("piece");
        goods.setSize("standard");
        goods.setDescription("integration test fixture");
        goods.setPurchasePrice(new BigDecimal("4.00"));
        goods.setAvgCostPrice(new BigDecimal("4.00"));
        goods.setLastPurchasePrice(new BigDecimal("4.00"));
        goods.setSalePrice(salePrice);
        goods.setVipPrice(salePrice);
        goods.setCoupon(BigDecimal.ZERO);
        goods.setStock(stock);
        goods.setSales(0L);
        goods.setStatus("SALE");
        goods.setIsDiscountParticipable(1);
        goods.setIsCombo(0);
        goods.setTenantId(0L);
        gmsGoodsMapper.insert(goods);
        return goods;
    }

    public UmsMember createMember(String suffix, BigDecimal balance) {
        UmsMember member = new UmsMember();
        member.setCode("CARD-" + suffix);
        member.setName("M" + suffix);
        member.setType("NORMAL");
        member.setPhone("138" + suffix);
        member.setProvince("");
        member.setCity("");
        member.setDistrict("");
        member.setAddress("");
        member.setCoupon(BigDecimal.ZERO);
        member.setConsumeAmount(BigDecimal.ZERO);
        member.setConsumeCoupon(BigDecimal.ZERO);
        member.setConsumeTimes(0);
        member.setCancelTimes(0);
        member.setRemark("integration test fixture");
        member.setDeleted(false);
        member.setTenantId(0L);
        member.setBalance(balance);
        umsMemberMapper.insert(member);
        return member;
    }

    public PosCouponRule createCouponRule(String suffix, BigDecimal threshold, BigDecimal discount) {
        PosCouponRule rule = new PosCouponRule();
        rule.setName("C" + suffix);
        rule.setThresholdAmount(threshold);
        rule.setDiscountAmount(discount);
        rule.setStatus(1);
        rule.setCreateBy("test");
        rule.setTenantId("0");
        posCouponRuleMapper.insert(rule);
        return rule;
    }

    public PosMemberCoupon issueCoupon(Long memberId, Long ruleId) {
        PosMemberCoupon coupon = new PosMemberCoupon();
        coupon.setMemberId(memberId);
        coupon.setRuleId(ruleId);
        coupon.setStatus("UNUSED");
        coupon.setTenantId("0");
        posMemberCouponMapper.insert(coupon);
        return coupon;
    }

    public SettleAccountsDTO cashSettlement(String requestId, Long goodsId, int quantity, BigDecimal paidAmount) {
        OmsOrderDetailDTO line = new OmsOrderDetailDTO();
        line.setGoodsId(goodsId);
        line.setQuantity(quantity);

        SettleAccountsDTO.PaymentItem payment = new SettleAccountsDTO.PaymentItem();
        payment.setPayMethodCode("CASH");
        payment.setPayMethodName("Cash");
        payment.setPayAmount(paidAmount);

        SettleAccountsDTO request = new SettleAccountsDTO();
        request.setReqId(requestId);
        request.setOrderDetail(Collections.singletonList(line));
        request.setPayments(Collections.singletonList(payment));
        request.setWaiveCoupon(false);
        request.setManualDiscountAmount(BigDecimal.ZERO);
        return request;
    }
    public SettleAccountsDTO couponSettlement(String requestId, Long memberId, Long goodsId, int quantity,
                                               BigDecimal paidAmount, Long ruleId) {
        SettleAccountsDTO request = cashSettlement(requestId, goodsId, quantity, paidAmount);
        request.setMember(memberId);
        request.setUsedCouponRuleId(ruleId);
        request.setUsedCouponCount(1);
        return request;
    }

    public SettleAccountsDTO mixedCashBalanceSettlement(String requestId, Long memberId, Long goodsId,
                                                         int quantity, BigDecimal cashAmount, BigDecimal balanceAmount) {
        SettleAccountsDTO request = cashSettlement(requestId, goodsId, quantity, cashAmount);
        request.setMember(memberId);
        SettleAccountsDTO.PaymentItem balance = new SettleAccountsDTO.PaymentItem();
        balance.setPayMethodCode("BALANCE");
        balance.setPayMethodName("Balance");
        balance.setPayAmount(balanceAmount);
        request.setPayments(Arrays.asList(request.getPayments().get(0), balance));
        return request;
    }

    public SettleAccountsDTO balanceSettlement(String requestId, Long memberId, Long goodsId, int quantity, BigDecimal paidAmount) {
        SettleAccountsDTO request = cashSettlement(requestId, goodsId, quantity, paidAmount);
        request.setMember(memberId);
        request.getPayments().get(0).setPayMethodCode("BALANCE");
        request.getPayments().get(0).setPayMethodName("Balance");
        return request;
    }
}
