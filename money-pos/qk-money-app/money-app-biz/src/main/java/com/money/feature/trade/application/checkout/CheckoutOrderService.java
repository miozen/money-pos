package com.money.feature.trade.application.checkout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus; // 🌟 引入刚确认的全局错误码字典！
import com.money.constant.OrderStatusEnum;
import com.money.dto.pos.PricingItemResult;
import com.money.dto.pos.PricingResult;
import com.money.entity.GmsGoods;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.UmsMember;
import com.money.entity.GmsGoodsCategory;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.feature.trade.domain.order.OmsOrderDetailService;
import com.money.feature.gms.application.catalog.GmsGoodsCategoryService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutOrderService {

    private final OmsOrderDetailService omsOrderDetailService;
    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final GmsGoodsCategoryService gmsGoodsCategoryService;

    public boolean loadExistingOrder(CheckoutContext context) {
        String reqId = context.getRequest().getReqId();
        OmsOrder existingOrder = omsOrderMapper.selectOne(new LambdaQueryWrapper<OmsOrder>().eq(OmsOrder::getOrderNo, reqId));
        if (existingOrder == null) return false;

        java.util.List<OmsOrderDetail> existingDetails = omsOrderDetailMapper.selectList(new LambdaQueryWrapper<OmsOrderDetail>().eq(OmsOrderDetail::getOrderNo, reqId));
        context.setOrder(existingOrder);
        context.setOrderDetails(existingDetails);
        return true;
    }

    public void createOrder(CheckoutContext context) {
        PricingResult trialRes = context.getPricingResult();
        UmsMember verifiedMember = context.getMember();
        Map<Long, GmsGoods> goodsMap = context.getGoodsMap();
        String orderNo = context.getRequest().getReqId();

        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);

        order.setRetailAmount(trialRes.getRetailAmount());
        order.setMemberAmount(trialRes.getMemberAmount());
        order.setPrivilegeAmount(trialRes.getPrivilegeAmount());
        order.setActualCouponDeduct(trialRes.getActualCouponDeduct());
        order.setWaivedCouponAmount(trialRes.getWaivedCouponAmount());

        order.setTotalAmount(trialRes.getRetailAmount());
        order.setCouponAmount(trialRes.getActualCouponDeduct());

        order.setUseVoucherAmount(trialRes.getVoucherDeduct());
        order.setManualDiscountAmount(trialRes.getManualDeduct());
        order.setCostAmount(trialRes.getCostAmount());
        order.setPayAmount(trialRes.getFinalPayAmount());
        order.setFinalSalesAmount(trialRes.getFinalPayAmount());

        order.setStatus(OrderStatusEnum.PAID.name());
        order.setPaymentTime(LocalDateTime.now());

        // ==========================================
        // 🛡️ 第三道防线：铁面判官，坚决拦截“金额不平”的订单！
        // ==========================================
        BigDecimal targetFinalPay = trialRes.getFinalPayAmount() != null ? trialRes.getFinalPayAmount() : BigDecimal.ZERO;
        BigDecimal actualTotalPaid = BigDecimal.ZERO;

        if (context.getRequest() != null && context.getRequest().getPayments() != null) {
            // 🌟 完美修复：使用 Java 8 原生 Stream，让编译器自动推断类型，彻底告别 var！
            actualTotalPaid = context.getRequest().getPayments().stream()
                    .map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 金额核算：如果收银员手滑少填了，或者黑客篡改了前端请求...
        if (actualTotalPaid.compareTo(targetFinalPay) < 0) {
            log.error("🚨 拦截非法入账请求！单号: {}, 真实应收: {}, 实际上传支付总计: {}", orderNo, targetFinalPay, actualTotalPaid);
            throw new BaseException(BizErrorStatus.POS_PAYMENT_NOT_ENOUGH.getMessage());
        }
        // ==========================================

        if (verifiedMember != null) {
            order.setVip(true);
            order.setMemberId(verifiedMember.getId());
            order.setMember(verifiedMember.getName());
            order.setContact(verifiedMember.getPhone());
        } else {
            order.setVip(false);
        }

        try {
            omsOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("⚠️ 拦截到重复的下单请求，ReqID已被占用: {}", orderNo);
            throw new BaseException("订单已生成，请勿重复点击下单！");
        } catch (Exception e) {
            log.error("💥 订单落库发生未知系统故障，单号: {}", orderNo, e);
            throw new BaseException("系统开小差了，订单可能未保存，请联系管理员核实单号：" + orderNo);
        }

        java.util.List<OmsOrderDetail> details = new ArrayList<>();
        for (PricingItemResult itemRes : trialRes.getItems()) {
            GmsGoods goods = goodsMap.get(itemRes.getGoodsId());
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setOrderNo(orderNo);
            detail.setStatus(OrderStatusEnum.PAID.name());
            detail.setGoodsId(goods.getId());
            detail.setBrandId(goods.getBrandId());
            detail.setGoodsBarcode(goods.getBarcode());
            detail.setGoodsName(goods.getName());
            detail.setSalePrice(itemRes.getUnitOriginalPrice());
            detail.setPurchasePrice(itemRes.getCostPrice() != null ? itemRes.getCostPrice() : BigDecimal.ZERO);
            detail.setVipPrice(goods.getVipPrice());
            detail.setQuantity(itemRes.getQuantity());
            detail.setGoodsPrice(itemRes.getUnitRealPrice());

            // 🌟 核心修复3：彻底抛弃假账！只存真正的单品核销额！
            detail.setCoupon(itemRes.getActualSubTotalCoupon() != null ? itemRes.getActualSubTotalCoupon() : BigDecimal.ZERO);

            if (goods.getCategoryId() != null) {
                detail.setCategoryId(goods.getCategoryId());
                GmsGoodsCategory category = gmsGoodsCategoryService.getById(goods.getCategoryId());
                if (category != null) {
                    detail.setCategoryName(category.getName());
                }
            }
            details.add(detail);
        }
        omsOrderDetailService.saveBatch(details);

        context.setOrder(order);
        context.setOrderDetails(details);
    }
}
