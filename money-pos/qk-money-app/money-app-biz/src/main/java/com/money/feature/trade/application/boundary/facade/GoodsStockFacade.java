package com.money.feature.trade.application.boundary.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.entity.GmsGoods;
import com.money.entity.GmsGoodsCombo;
import com.money.entity.GmsInventoryDoc;
import com.money.entity.GmsInventoryDocItem;
import com.money.entity.GmsStockLog;
import com.money.entity.OmsOrderDetail;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocItemMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.service.GmsStockLogService;
import com.money.feature.trade.application.boundary.facade.dto.RefundStockLine;
import com.money.feature.trade.application.boundary.facade.dto.RefundStockRequest;
import com.money.feature.trade.application.boundary.facade.dto.SaleStockLine;
import com.money.feature.trade.application.boundary.facade.dto.SaleStockRequest;
import com.money.feature.trade.application.support.PosInventoryActionService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Boundary for transaction use cases that change GMS stock. */
@Service
@RequiredArgsConstructor
public class GoodsStockFacade {

    private final PosInventoryActionService inventoryActionService;
    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;
    private final GmsStockLogService gmsStockLogService;
    private final GmsInventoryDocMapper inventoryDocMapper;
    private final GmsInventoryDocItemMapper inventoryDocItemMapper;

    public void deductForSale(SaleStockRequest request) {
        List<OmsOrderDetail> orderDetails = new ArrayList<>();
        Map<Long, GmsGoods> goodsMap = new HashMap<>();
        for (SaleStockLine line : request.getLines()) {
            OmsOrderDetail detail = new OmsOrderDetail();
            detail.setGoodsId(line.getGoodsId());
            detail.setGoodsName(line.getGoodsName());
            detail.setGoodsBarcode(line.getGoodsBarcode());
            detail.setQuantity(line.getQuantity());
            detail.setPurchasePrice(line.getPurchasePrice());
            orderDetails.add(detail);

            GmsGoods goods = new GmsGoods();
            goods.setId(line.getGoodsId());
            goods.setName(line.getGoodsName());
            goods.setIsCombo(Boolean.TRUE.equals(line.getCombo()) ? 1 : 0);
            goodsMap.put(line.getGoodsId(), goods);
        }

        inventoryActionService.deduct(orderDetails, goodsMap, request.getOrderNo());
        createSaleOutDocument(request);
    }

    public BigDecimal restoreForRefund(RefundStockRequest request) {
        GmsInventoryDoc doc = createReturnDoc();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (RefundStockLine line : request.getLines()) {
            totalCost = totalCost.add(restoreLine(request.getOrderNo(), line, doc));
        }
        doc.setTotalAmount(totalCost);
        inventoryDocMapper.updateById(doc);
        return totalCost;
    }

    private void createSaleOutDocument(SaleStockRequest request) {
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("XS-" + request.getOrderNo());
        doc.setDocType("SALE_OUT");
        doc.setRemark("front desk settlement, order: " + request.getOrderNo());
        doc.setCreateTime(LocalDateTime.now());

        BigDecimal totalCostAmount = BigDecimal.ZERO;
        for (SaleStockLine line : request.getLines()) {
            BigDecimal qty = new BigDecimal(line.getQuantity());
            BigDecimal cost = line.getPurchasePrice() != null ? line.getPurchasePrice() : BigDecimal.ZERO;
            totalCostAmount = totalCostAmount.add(cost.multiply(qty));
        }
        doc.setTotalAmount(totalCostAmount.negate());
        inventoryDocMapper.insert(doc);

        for (SaleStockLine line : request.getLines()) {
            GmsInventoryDocItem item = new GmsInventoryDocItem();
            item.setDocNo(doc.getDocNo());
            item.setGoodsId(line.getGoodsId());
            item.setGoodsName(line.getGoodsName());
            item.setBarcode(line.getGoodsBarcode());
            item.setChangeQty(-line.getQuantity());
            item.setCostPrice(line.getPurchasePrice() != null ? line.getPurchasePrice() : BigDecimal.ZERO);
            inventoryDocItemMapper.insert(item);
        }
    }

    private GmsInventoryDoc createReturnDoc() {
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("TH" + System.currentTimeMillis());
        doc.setDocType("RETURN");
        doc.setCreateTime(LocalDateTime.now());
        inventoryDocMapper.insert(doc);
        return doc;
    }

    private BigDecimal restoreLine(String orderNo, RefundStockLine line, GmsInventoryDoc doc) {
        GmsGoods goods = gmsGoodsMapper.selectById(line.getGoodsId());
        if (goods == null) {
            throw new BaseException("goods not found: " + line.getGoodsId());
        }

        BigDecimal impact = BigDecimal.ZERO;
        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
            int latestComboStock = latestStock(goods.getId());
            recordStockLog(goods, line.getQuantity(), latestComboStock, orderNo, BigDecimal.ZERO, "refund combo allocation");
            saveDocItem(doc.getDocNo(), goods, line.getQuantity(), BigDecimal.ZERO, latestComboStock);

            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>()
                    .eq(GmsGoodsCombo::getComboGoodsId, goods.getId()));
            for (GmsGoodsCombo combo : combos) {
                GmsGoods sub = gmsGoodsMapper.selectById(combo.getSubGoodsId());
                if (sub == null) {
                    continue;
                }
                int qty = Math.multiplyExact(line.getQuantity(), combo.getSubGoodsQty());
                gmsGoodsMapper.addStockAtomically(sub.getId(), new BigDecimal(qty));
                int latestStock = latestStock(sub.getId());
                BigDecimal cost = sub.getAvgCostPrice() != null ? sub.getAvgCostPrice()
                        : (sub.getPurchasePrice() != null ? sub.getPurchasePrice() : BigDecimal.ZERO);
                impact = impact.add(cost.multiply(new BigDecimal(qty)));
                recordStockLog(sub, qty, latestStock, orderNo, cost, "refund combo physical stock");
                saveDocItem(doc.getDocNo(), sub, qty, cost, latestStock);
            }
        } else {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
            int latestStock = latestStock(goods.getId());
            BigDecimal cost = line.getPurchasePrice() != null ? line.getPurchasePrice() : BigDecimal.ZERO;
            impact = cost.multiply(new BigDecimal(line.getQuantity()));
            recordStockLog(goods, line.getQuantity(), latestStock, orderNo, cost, "refund goods stock");
            saveDocItem(doc.getDocNo(), goods, line.getQuantity(), cost, latestStock);
        }
        return impact;
    }

    private int latestStock(Long goodsId) {
        GmsGoods goods = gmsGoodsMapper.selectById(goodsId);
        return goods != null && goods.getStock() != null ? goods.getStock().intValue() : 0;
    }

    private void saveDocItem(String docNo, GmsGoods goods, int qty, BigDecimal cost, int latestStock) {
        GmsInventoryDocItem item = new GmsInventoryDocItem();
        item.setDocNo(docNo);
        item.setGoodsId(goods.getId());
        item.setGoodsName(goods.getName());
        item.setBarcode(goods.getBarcode());
        item.setChangeQty(qty);
        item.setCostPrice(cost);
        item.setPreStock((long) (latestStock - qty));
        item.setAfterStock((long) latestStock);
        inventoryDocItemMapper.insert(item);
    }

    private void recordStockLog(GmsGoods goods, int qty, int latestStock, String orderNo, BigDecimal cost, String remark) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(goods.getId());
        log.setGoodsName(goods.getName());
        log.setGoodsBarcode(goods.getBarcode());
        log.setType("RETURN");
        log.setQuantity(qty);
        log.setAfterQuantity(latestStock);
        log.setOrderNo(orderNo);
        log.setCostPriceSnapshot(cost);
        log.setImpactAmount(cost.multiply(new BigDecimal(qty)));
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        gmsStockLogService.save(log);
    }
}
