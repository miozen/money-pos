package com.money.feature.trade.application.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.dto.pos.PricingItemResult;
import com.money.dto.pos.PricingResult;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.entity.GmsGoods;
import com.money.entity.PosCouponRule;
import com.money.entity.PosSkuLevelPrice;
import com.money.entity.UmsMemberBrandLevel;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.service.GmsGoodsService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosCalculationEngine {

    private final GmsGoodsService gmsGoodsService;
    private final UmsMemberBrandLevelMapper brandLevelMapper;
    private final PosSkuLevelPriceMapper skuLevelPriceMapper;
    private final PosCouponRuleMapper couponRuleMapper;

    private static class CalcContext {
        SettleTrialReqDTO req;
        PricingResult result;
        Map<Long, GmsGoods> goodsMap;
        Map<String, String> memberBrandLevels = new HashMap<>();
        Map<Long, List<PosSkuLevelPrice>> skuPriceMap = new HashMap<>();
        BigDecimal totalConfiguredCoupon = BigDecimal.ZERO;
    }

    public PricingResult calculate(SettleTrialReqDTO req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return new PricingResult();
        }
        CalcContext ctx = initContext(req);
        calculateBasePrices(ctx);
        dispatchSettlementTrack(ctx);
        calculateMarketingDeduct(ctx);
        return formatScale(ctx.result);
    }

    private CalcContext initContext(SettleTrialReqDTO req) {
        CalcContext ctx = new CalcContext();
        ctx.req = req;
        ctx.result = new PricingResult();
        ctx.result.setManualDeduct(req.getManualDiscountAmount() != null ? req.getManualDiscountAmount() : BigDecimal.ZERO);
        ctx.totalConfiguredCoupon = BigDecimal.ZERO;

        List<Long> goodsIds = req.getItems().stream().map(SettleTrialReqDTO.TrialItem::getGoodsId).collect(Collectors.toList());
        ctx.goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

        if (req.getMember() != null) {
            List<UmsMemberBrandLevel> levels = brandLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, req.getMember()));
            levels.forEach(l -> {
                if (l.getBrand() != null) ctx.memberBrandLevels.put(l.getBrand().trim(), l.getLevelCode());
            });
            ctx.skuPriceMap = skuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds))
                    .stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));
        }
        return ctx;
    }

    private void calculateBasePrices(CalcContext ctx) {
        boolean isWaive = Boolean.TRUE.equals(ctx.req.getWaiveCoupon());

        for (SettleTrialReqDTO.TrialItem reqItem : ctx.req.getItems()) {
            GmsGoods goods = ctx.goodsMap.get(reqItem.getGoodsId());
            if (goods == null) throw new BaseException("【试算拦截】发现不存在或已下架的商品ID: " + reqItem.getGoodsId());

            long currentStock = goods.getStock() != null ? goods.getStock() : 0L;
            if (reqItem.getQuantity() > currentStock) {
                throw new BaseException(BizErrorStatus.STOCK_NOT_ENOUGH, "结算中断！商品【{0}】库存不足，当前仅剩 {1} 件。", goods.getName(), currentStock).withData(currentStock);
            }

            BigDecimal qty = new BigDecimal(reqItem.getQuantity());
            BigDecimal unitOriginalPrice = goods.getSalePrice() != null ? goods.getSalePrice() : BigDecimal.ZERO;
            BigDecimal unitRealPrice = unitOriginalPrice;
            BigDecimal unitCoupon = BigDecimal.ZERO;
            BigDecimal unitCost = goods.getAvgCostPrice() != null ? goods.getAvgCostPrice() : (goods.getPurchasePrice() != null ? goods.getPurchasePrice() : BigDecimal.ZERO);

            String brandKey = goods.getBrandId() != null ? String.valueOf(goods.getBrandId()) : "";
            String levelCode = ctx.memberBrandLevels.get(brandKey);

            if (levelCode != null && ctx.skuPriceMap.containsKey(goods.getId())) {
                for (PosSkuLevelPrice sp : ctx.skuPriceMap.get(goods.getId())) {
                    if (levelCode.equals(sp.getLevelId())) {
                        if (sp.getMemberPrice() == null || sp.getMemberPrice().compareTo(BigDecimal.ZERO) < 0) {
                            throw new BaseException("商品「" + goods.getName() + "」的会员价配置异常");
                        }
                        unitRealPrice = sp.getMemberPrice();
                        unitCoupon = sp.getMemberCoupon() != null ? sp.getMemberCoupon() : BigDecimal.ZERO;
                        break;
                    }
                }
            }

            // 🌟 终极修复 1：根治“会员价倒挂”。宽容降级：会员价绝不能高于零售价
            if (unitRealPrice.compareTo(unitOriginalPrice) > 0) {
                log.warn("【价格倒挂预警】商品[{}]的会员价({})高于零售价({})，已自动向下修正为零售价。", goods.getName(), unitRealPrice, unitOriginalPrice);
                unitRealPrice = unitOriginalPrice;
            }

            BigDecimal subTotalRetail = unitOriginalPrice.multiply(qty);
            BigDecimal subTotalMember = unitRealPrice.multiply(qty);
            BigDecimal subTotalPrivilege = subTotalRetail.subtract(subTotalMember);
            BigDecimal itemTotalCost = unitCost.multiply(qty);

            BigDecimal subTotalCoupon = unitCoupon.multiply(qty);

            if (subTotalCoupon.compareTo(subTotalPrivilege) > 0) {
                subTotalCoupon = subTotalPrivilege;
            }
            if (subTotalCoupon.compareTo(BigDecimal.ZERO) < 0) {
                subTotalCoupon = BigDecimal.ZERO;
            }

            if (isWaive) {
                subTotalCoupon = BigDecimal.ZERO;
            }

            PricingItemResult itemRes = new PricingItemResult();
            itemRes.setGoodsId(goods.getId());
            itemRes.setQuantity(reqItem.getQuantity());
            itemRes.setUnitOriginalPrice(unitOriginalPrice);
            itemRes.setUnitRealPrice(unitRealPrice);
            itemRes.setCostPrice(unitCost);
            itemRes.setSubTotalRetail(subTotalRetail);
            itemRes.setSubTotalMember(subTotalMember);
            itemRes.setSubTotalPrivilege(subTotalPrivilege);
            itemRes.setActualSubTotalCoupon(subTotalCoupon);
            ctx.result.getItems().add(itemRes);

            ctx.result.setRetailAmount(ctx.result.getRetailAmount().add(subTotalRetail));
            ctx.result.setMemberAmount(ctx.result.getMemberAmount().add(subTotalMember));
            ctx.result.setPrivilegeAmount(ctx.result.getPrivilegeAmount().add(subTotalPrivilege));
            ctx.result.setCostAmount(ctx.result.getCostAmount().add(itemTotalCost));

            ctx.totalConfiguredCoupon = ctx.totalConfiguredCoupon.add(subTotalCoupon);

            if (goods.getIsDiscountParticipable() != null && goods.getIsDiscountParticipable() == 1) {
                ctx.result.setParticipatingAmount(ctx.result.getParticipatingAmount().add(subTotalMember));
            }
        }
    }

    private void dispatchSettlementTrack(CalcContext ctx) {
        BigDecimal totalPrivilege = ctx.result.getPrivilegeAmount();
        BigDecimal configuredCoupon = ctx.totalConfiguredCoupon;

        ctx.result.setActualCouponDeduct(configuredCoupon);

        // 🌟 终极修复 2：店铺让利绝不可能为负数 (防御性兜底)
        BigDecimal waived = totalPrivilege.subtract(configuredCoupon);
        ctx.result.setWaivedCouponAmount(waived.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : waived);
    }

    private void calculateMarketingDeduct(CalcContext ctx) {
        if (ctx.req.getUsedCouponRuleId() != null && ctx.req.getUsedCouponCount() != null && ctx.req.getUsedCouponCount() > 0) {
            PosCouponRule rule = couponRuleMapper.selectById(ctx.req.getUsedCouponRuleId());
            if (rule == null) throw new BaseException("【试算拦截】选用的满减券规则不存在");

            BigDecimal requiredAmount = rule.getThresholdAmount().multiply(new BigDecimal(ctx.req.getUsedCouponCount()));
            if (ctx.result.getParticipatingAmount().compareTo(requiredAmount) < 0) {
                throw new BaseException(BizErrorStatus.COUPON_NOT_ENOUGH, "【风控】参与满减活动商品总额未达到门槛");
            }
            ctx.result.setVoucherDeduct(rule.getDiscountAmount().multiply(new BigDecimal(ctx.req.getUsedCouponCount())));
        }

        BigDecimal maxAllowedManual = ctx.result.getMemberAmount().subtract(ctx.result.getVoucherDeduct());
        if (ctx.result.getManualDeduct().compareTo(maxAllowedManual) > 0) {
            throw new BaseException(BizErrorStatus.POS_MANUAL_DISCOUNT_EXCEED, "【风控拦截】手工优惠额超限");
        }

        BigDecimal finalPay = ctx.result.getMemberAmount()
                .subtract(ctx.result.getVoucherDeduct())
                .subtract(ctx.result.getManualDeduct());

        ctx.result.setFinalPayAmount(finalPay.compareTo(BigDecimal.ZERO) > 0 ? finalPay : BigDecimal.ZERO);
    }

    private PricingResult formatScale(PricingResult res) {
        res.setRetailAmount(res.getRetailAmount().setScale(2, RoundingMode.HALF_UP));
        res.setMemberAmount(res.getMemberAmount().setScale(2, RoundingMode.HALF_UP));
        res.setPrivilegeAmount(res.getPrivilegeAmount().setScale(2, RoundingMode.HALF_UP));
        res.setActualCouponDeduct(res.getActualCouponDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setWaivedCouponAmount(res.getWaivedCouponAmount().setScale(2, RoundingMode.HALF_UP));
        res.setVoucherDeduct(res.getVoucherDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setManualDeduct(res.getManualDeduct().setScale(2, RoundingMode.HALF_UP));
        res.setFinalPayAmount(res.getFinalPayAmount().setScale(2, RoundingMode.HALF_UP));
        return res;
    }
}