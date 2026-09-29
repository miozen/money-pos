package com.money.feature.trade.application.memberpickup;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.PayMethodEnum;
import com.money.contract.goods.CheckoutGoodsQuery;
import com.money.contract.goods.CheckoutGoodsSnapshot;
import com.money.contract.goods.MemberPickupReturnStockCommand;
import com.money.contract.goods.MemberPickupReturnStockCommandHandler;
import com.money.contract.goods.MemberPickupStockCommand;
import com.money.contract.goods.MemberPickupStockCommandHandler;
import com.money.contract.goods.StockMutationLine;
import com.money.contract.member.*;
import com.money.dto.pos.*;
import com.money.feature.trade.infrastructure.persistence.entity.*;
import com.money.feature.trade.infrastructure.persistence.mapper.*;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** ME-1.3 orchestration: non-product receipts own money; UMS owns rights and GMS owns physical stock. */
@Service @RequiredArgsConstructor
public class MemberAmountBenefitService {
    private final OmsMemberAmountReceiptMapper receiptMapper;
    private final OmsMemberAmountReceiptPayMapper receiptPayMapper;
    private final OmsMemberAmountPickupMapper pickupMapper;
    private final OmsMemberAmountPickupItemMapper pickupItemMapper;
    private final MemberBrandBenefitTierQuery tierQuery;
    private final MemberBrandBenefitLedgerCommandHandler ledgerCommandHandler;
    private final MemberAmountRightQuery amountRightQuery;
    private final MemberAmountPickupCommandHandler amountPickupCommandHandler;
    private final MemberAmountPickupRefundCommandHandler amountPickupRefundCommandHandler;
    private final MemberBalancePaymentCommandHandler balancePaymentCommandHandler;
    private final CheckoutGoodsQuery goodsQuery;
    private final MemberPickupStockCommandHandler pickupStockCommandHandler;
    private final MemberPickupReturnStockCommandHandler pickupReturnStockCommandHandler;

    @Transactional(rollbackFor = Exception.class)
    public MemberAmountPackagePurchaseVO purchase(MemberAmountPackagePurchaseDTO dto) {
        require(dto != null && dto.getMemberId() != null && text(dto.getBrandId()) && text(dto.getTierCode()) && text(dto.getReqId()), "金额权益包购买请求不完整");
        OmsMemberAmountReceipt existing = receiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountReceipt>().eq(OmsMemberAmountReceipt::getRequestNo, dto.getReqId()));
        if (existing != null) return purchaseResult(existing);
        MemberBrandBenefitTierSnapshot tier = tierQuery.findEnabled(dto.getBrandId(), dto.getTierCode());
        require(tier != null && positive(tier.getConfiguredAmount()), "品牌金额权益档位不存在或未启用");
        List<Payment> payments = payments(dto.getPayments(), tier.getConfiguredAmount());
        String receiptNo = no("MAR");
        OmsMemberAmountReceipt receipt = receipt(receiptNo, dto.getReqId(), "AMOUNT_PACKAGE_PURCHASE", dto.getMemberId(), dto.getBrandId(),
                tier.getConfiguredAmount(), BigDecimal.ZERO, tier.getConfiguredAmount(), BigDecimal.ZERO, "COMPLETED");
        receiptMapper.insert(receipt); writePayments(receiptNo, dto.getMemberId(), dto.getReqId(), payments, false);
        MemberBrandBenefitLedgerCommand.AmountGrant grant = new MemberBrandBenefitLedgerCommand.AmountGrant();
        grant.setMemberId(dto.getMemberId()); grant.setBrandId(dto.getBrandId()); grant.setTierCode(dto.getTierCode()); grant.setSourceReceiptNo(receiptNo);
        grant.setAmount(tier.getConfiguredAmount()); grant.setRequestNo(dto.getReqId()); grant.setReason("AMOUNT_PACKAGE_PURCHASE");
        Long rightId = ledgerCommandHandler.grantAmount(grant);
        OmsMemberAmountReceipt update = new OmsMemberAmountReceipt(); update.setId(receipt.getId()); update.setAmountRightId(rightId); receiptMapper.updateById(update);
        return purchaseResult(receiptNo, rightId, tier.getConfiguredAmount());
    }

    @Transactional(rollbackFor = Exception.class)
    public MemberAmountPickupVO pickup(MemberAmountPickupDTO dto) {
        require(dto != null && dto.getMemberId() != null && dto.getAmountRightId() != null && text(dto.getReqId()) && dto.getLines() != null && !dto.getLines().isEmpty(), "金额权益提货请求不完整");
        OmsMemberAmountPickup existing = pickupMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountPickup>().eq(OmsMemberAmountPickup::getRequestNo, dto.getReqId()));
        if (existing != null) return pickupResult(existing);
        MemberAmountRightSnapshot right = amountRightQuery.findAvailableForPickup(dto.getMemberId(), dto.getAmountRightId());
        require(right != null, "金额权益不存在或不可用");
        Map<Long, CheckoutGoodsSnapshot> goods = goodsQuery.findByIds(goodsIds(dto.getLines()));
        List<PickupLine> lines = pickupLines(dto.getLines(), goods, right);
        BigDecimal total = sum(lines, false); BigDecimal cost = sum(lines, true);
        BigDecimal rightDeduct = min(total, right.getRemainingAmount()); BigDecimal supplement = total.subtract(rightDeduct);
        List<Payment> payments = payments(dto.getPayments(), supplement);
        String pickupNo = no("MAP"); String receiptNo = no("MAR");
        OmsMemberAmountPickup pickup = new OmsMemberAmountPickup(); pickup.setPickupNo(pickupNo); pickup.setRequestNo(dto.getReqId()); pickup.setMemberId(dto.getMemberId());
        pickup.setAmountRightId(right.getRightId()); pickup.setSupplementReceiptNo(receiptNo); pickup.setStatus("COMPLETED"); pickupMapper.insert(pickup);
        OmsMemberAmountReceipt receipt = receipt(receiptNo, dto.getReqId() + "-SUP", "AMOUNT_PICKUP_SUPPLEMENT", dto.getMemberId(), right.getBrandId(), total, rightDeduct, supplement, cost, "COMPLETED");
        receipt.setAmountRightId(right.getRightId()); receipt.setSourcePickupNo(pickupNo); receipt.setSourceReceiptNo(right.getSourceReceiptNo()); receiptMapper.insert(receipt);
        if (positive(rightDeduct)) { MemberAmountPickupCommand command = new MemberAmountPickupCommand(); command.setMemberId(dto.getMemberId()); command.setRightId(right.getRightId()); command.setPickupNo(pickupNo); command.setRequestNo(dto.getReqId()); command.setAmount(rightDeduct); amountPickupCommandHandler.handle(command); }
        writePayments(receiptNo, dto.getMemberId(), dto.getReqId(), payments, false);
        MemberPickupStockCommand stock = new MemberPickupStockCommand(); stock.setPickupNo(pickupNo); stock.setLines(stockLines(lines)); pickupStockCommandHandler.handle(stock);
        for (PickupLine line : lines) { OmsMemberAmountPickupItem item = new OmsMemberAmountPickupItem(); item.setPickupNo(pickupNo); item.setGoodsId(line.goods.getId()); item.setQuantity(line.quantity); item.setUnitPrice(line.unitPrice); item.setLineAmount(line.amount); item.setPurchasePrice(line.cost); pickupItemMapper.insert(item); }
        return pickupResult(pickupNo, receiptNo, rightDeduct, supplement);
    }

    @Transactional(rollbackFor = Exception.class)
    public MemberAmountPickupRefundVO refund(MemberAmountPickupRefundDTO dto) {
        require(dto != null && text(dto.getPickupNo()) && text(dto.getReqId()), "金额权益提货退款请求不完整");
        OmsMemberAmountPickup pickup = pickupMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountPickup>().eq(OmsMemberAmountPickup::getPickupNo, dto.getPickupNo()));
        require(pickup != null, "金额权益提货单不存在");
        require("COMPLETED".equals(pickup.getStatus()), "金额权益提货单已退款，不能重复退款");
        String refundNo = no("MARF");
        OmsMemberAmountReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountReceipt>().eq(OmsMemberAmountReceipt::getReceiptNo, pickup.getSupplementReceiptNo()));
        require(receipt != null && "COMPLETED".equals(receipt.getStatus()), "补差凭证状态异常");
        if (positive(receipt.getRightDeductAmount())) { MemberAmountPickupRefundCommand command = new MemberAmountPickupRefundCommand(); command.setMemberId(pickup.getMemberId()); command.setRightId(pickup.getAmountRightId()); command.setPickupNo(pickup.getPickupNo()); command.setRefundNo(refundNo); command.setRequestNo(dto.getReqId()); command.setAmount(receipt.getRightDeductAmount()); amountPickupRefundCommandHandler.handle(command); }
        List<OmsMemberAmountPickupItem> items = pickupItemMapper.selectList(new LambdaQueryWrapper<OmsMemberAmountPickupItem>().eq(OmsMemberAmountPickupItem::getPickupNo, pickup.getPickupNo()));
        require(!items.isEmpty(), "金额权益提货明细不存在，不支持部分退款");
        Map<Long, CheckoutGoodsSnapshot> goods = goodsQuery.findByIds(items.stream().map(OmsMemberAmountPickupItem::getGoodsId).collect(java.util.stream.Collectors.toList()));
        MemberPickupReturnStockCommand stock = new MemberPickupReturnStockCommand(); stock.setReturnNo(refundNo); stock.setLines(returnLines(items, goods)); pickupReturnStockCommandHandler.handle(stock);
        List<OmsMemberAmountReceiptPay> originalPays = receiptPayMapper.selectList(new LambdaQueryWrapper<OmsMemberAmountReceiptPay>().eq(OmsMemberAmountReceiptPay::getReceiptNo, receipt.getReceiptNo()));
        List<Payment> reversal = new ArrayList<>(); for (OmsMemberAmountReceiptPay pay : originalPays) reversal.add(new Payment(pay.getPayMethodCode(), pay.getPayMethodName(), pay.getPayTag(), pay.getNetAmount().negate()));
        writePayments(receipt.getReceiptNo(), pickup.getMemberId(), dto.getReqId(), reversal, true);
        OmsMemberAmountReceipt receiptUpdate = new OmsMemberAmountReceipt(); receiptUpdate.setId(receipt.getId()); receiptUpdate.setStatus("REFUNDED"); receiptMapper.updateById(receiptUpdate);
        OmsMemberAmountPickup pickupUpdate = new OmsMemberAmountPickup(); pickupUpdate.setId(pickup.getId()); pickupUpdate.setStatus("REFUNDED"); pickupUpdate.setRefundNo(refundNo); pickupMapper.updateById(pickupUpdate);
        MemberAmountPickupRefundVO result = new MemberAmountPickupRefundVO(); result.setRefundNo(refundNo); return result;
    }

    private OmsMemberAmountReceipt receipt(String no, String request, String type, Long memberId, String brand, BigDecimal total, BigDecimal right, BigDecimal supplement, BigDecimal cost, String status) {
        OmsMemberAmountReceipt r = new OmsMemberAmountReceipt(); r.setReceiptNo(no); r.setRequestNo(request); r.setReceiptType(type); r.setMemberId(memberId); r.setBrandId(brand); r.setTotalAmount(scale(total)); r.setRightDeductAmount(scale(right)); r.setSupplementAmount(scale(supplement)); r.setCostAmount(scale(cost)); r.setStatus(status); return r;
    }
    private void writePayments(String receiptNo, Long memberId, String request, List<Payment> payments, boolean refund) {
        for (Payment p : payments) { if (p.amount.compareTo(BigDecimal.ZERO) == 0) continue; OmsMemberAmountReceiptPay pay = new OmsMemberAmountReceiptPay(); pay.setReceiptNo(receiptNo); pay.setPayMethodCode(p.code); pay.setPayMethodName(p.name); pay.setPayTag(p.tag); pay.setPayAmount(p.amount); pay.setOriginalAmount(p.amount); pay.setNetAmount(p.amount); pay.setChangeAllocated(BigDecimal.ZERO); receiptPayMapper.insert(pay);
            if (PayMethodEnum.BALANCE == PayMethodEnum.fromCode(p.code)) { MemberBalancePaymentCommand command = new MemberBalancePaymentCommand(); command.setMemberId(memberId); command.setReceiptNo(receiptNo); command.setRequestNo(request + "-" + p.code); command.setAmount(p.amount.abs()); command.setRefund(refund); balancePaymentCommandHandler.handle(command); }
        }
    }
    private List<PickupLine> pickupLines(List<MemberAmountPickupDTO.Line> request, Map<Long, CheckoutGoodsSnapshot> goods, MemberAmountRightSnapshot right) {
        Set<Long> seen = new HashSet<>(); List<PickupLine> result = new ArrayList<>();
        for (MemberAmountPickupDTO.Line line : request) { require(line != null && line.getGoodsId() != null && line.getQuantity() != null && line.getQuantity() > 0 && seen.add(line.getGoodsId()), "提货明细不合法或重复"); CheckoutGoodsSnapshot item = goods.get(line.getGoodsId()); require(item != null && String.valueOf(item.getBrandId()).equals(right.getBrandId()), "提货商品不属于该金额权益品牌"); BigDecimal unit = item.getLevelPrices().get(right.getPricingLevelCodeSnapshot()); require(unit != null && unit.compareTo(BigDecimal.ZERO) >= 0, "商品未配置权益价格档"); PickupLine value = new PickupLine(); value.goods = item; value.quantity = line.getQuantity(); value.unitPrice = scale(unit); value.amount = scale(unit.multiply(BigDecimal.valueOf(line.getQuantity()))); value.cost = scale((item.getAvgCostPrice() == null ? item.getPurchasePrice() : item.getAvgCostPrice()).multiply(BigDecimal.valueOf(line.getQuantity()))); result.add(value); }
        return result;
    }
    private List<StockMutationLine> stockLines(List<PickupLine> lines) { List<StockMutationLine> result = new ArrayList<>(); for (PickupLine l : lines) { StockMutationLine s = new StockMutationLine(); s.setGoodsId(l.goods.getId()); s.setGoodsName(l.goods.getName()); s.setGoodsBarcode(l.goods.getBarcode()); s.setQuantity(l.quantity); s.setPurchasePrice(l.goods.getPurchasePrice()); s.setCombo(l.goods.getIsCombo() != null && l.goods.getIsCombo() == 1); result.add(s); } return result; }
    private List<StockMutationLine> returnLines(List<OmsMemberAmountPickupItem> items, Map<Long, CheckoutGoodsSnapshot> goods) { List<StockMutationLine> result = new ArrayList<>(); for (OmsMemberAmountPickupItem i : items) { CheckoutGoodsSnapshot g = goods.get(i.getGoodsId()); require(g != null, "退货商品不存在"); StockMutationLine s = new StockMutationLine(); s.setGoodsId(g.getId()); s.setGoodsName(g.getName()); s.setGoodsBarcode(g.getBarcode()); s.setQuantity(i.getQuantity()); s.setPurchasePrice(i.getPurchasePrice()); result.add(s); } return result; }
    private List<Payment> payments(List<SettleAccountsDTO.PaymentItem> raw, BigDecimal expected) { if (!positive(expected)) { require(raw == null || raw.isEmpty(), "权益足额抵扣时不得录入补差支付"); return Collections.emptyList(); } require(raw != null && !raw.isEmpty(), "补差支付明细为空"); List<Payment> result = new ArrayList<>(); BigDecimal total = BigDecimal.ZERO; for (SettleAccountsDTO.PaymentItem p : raw) { require(p != null && positive(p.getPayAmount()) && text(p.getPayMethodCode()), "支付明细不合法"); PayMethodEnum method = PayMethodEnum.fromCode(p.getPayMethodCode().trim().toUpperCase()); require(method != null && (method != PayMethodEnum.AGGREGATE || text(p.getPayTag())), "支付方式或渠道标签不合法"); BigDecimal amount = scale(p.getPayAmount()); result.add(new Payment(method.getCode(), p.getPayMethodName() == null ? method.getCode() : p.getPayMethodName(), p.getPayTag(), amount)); total = total.add(amount); } require(scale(total).compareTo(scale(expected)) == 0, "支付金额必须等于业务凭证应收金额"); return result; }
    private List<Long> goodsIds(List<MemberAmountPickupDTO.Line> lines) { return lines.stream().map(MemberAmountPickupDTO.Line::getGoodsId).collect(java.util.stream.Collectors.toList()); }
    private BigDecimal sum(List<PickupLine> lines, boolean cost) { BigDecimal total = BigDecimal.ZERO; for (PickupLine line : lines) total = total.add(cost ? line.cost : line.amount); return scale(total); }
    private MemberAmountPackagePurchaseVO purchaseResult(OmsMemberAmountReceipt receipt) { return purchaseResult(receipt.getReceiptNo(), receipt.getAmountRightId(), receipt.getTotalAmount()); }
    private MemberAmountPackagePurchaseVO purchaseResult(String no, Long rightId, BigDecimal amount) { MemberAmountPackagePurchaseVO r = new MemberAmountPackagePurchaseVO(); r.setReceiptNo(no); r.setAmountRightId(rightId); r.setAmount(amount); return r; }
    private MemberAmountPickupVO pickupResult(OmsMemberAmountPickup pickup) { OmsMemberAmountReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountReceipt>().eq(OmsMemberAmountReceipt::getReceiptNo, pickup.getSupplementReceiptNo())); return pickupResult(pickup.getPickupNo(), pickup.getSupplementReceiptNo(), receipt.getRightDeductAmount(), receipt.getSupplementAmount()); }
    private MemberAmountPickupVO pickupResult(String pickup, String receipt, BigDecimal right, BigDecimal supplement) { MemberAmountPickupVO r = new MemberAmountPickupVO(); r.setPickupNo(pickup); r.setSupplementReceiptNo(receipt); r.setRightDeductAmount(right); r.setSupplementAmount(supplement); return r; }
    private static String no(String prefix) { return prefix + IdUtil.getSnowflakeNextIdStr(); }
    private static boolean text(String v) { return v != null && !v.trim().isEmpty(); }
    private static boolean positive(BigDecimal v) { return v != null && v.compareTo(BigDecimal.ZERO) > 0; }
    private static BigDecimal min(BigDecimal a, BigDecimal b) { return a.compareTo(b) <= 0 ? a : b; }
    private static BigDecimal scale(BigDecimal v) { return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP); }
    private static void require(boolean ok, String message) { if (!ok) throw new BaseException(message); }
    private static class PickupLine { CheckoutGoodsSnapshot goods; Integer quantity; BigDecimal unitPrice; BigDecimal amount; BigDecimal cost; }
    private static class Payment { final String code, name, tag; final BigDecimal amount; Payment(String c, String n, String t, BigDecimal a) { code=c; name=n; tag=t; amount=a; } }
}
