package com.money.feature.gms.application.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.contract.goods.RefundStockCommand;
import com.money.contract.goods.RefundStockCommandHandler;
import com.money.contract.goods.SaleStockCommand;
import com.money.contract.goods.SaleStockCommandHandler;
import com.money.contract.goods.StockMutationLine;
import com.money.entity.GmsGoods;
import com.money.entity.GmsGoodsCombo;
import com.money.entity.GmsInventoryDoc;
import com.money.entity.GmsStockLog;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDocItem;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocItemMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.service.GmsStockLogService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** GMS-owned inventory writes initiated by TRADE settlement and refund flows. */
@Service
@RequiredArgsConstructor
class GmsStockCommandService implements SaleStockCommandHandler, RefundStockCommandHandler {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;
    private final GmsStockLogService gmsStockLogService;
    private final GmsInventoryDocMapper inventoryDocMapper;
    private final GmsInventoryDocItemMapper inventoryDocItemMapper;

    @Override
    public void handle(SaleStockCommand command) {
        List<StockMutationLine> lines = command.getLines();
        Map<Long, GmsGoods> goodsMap = new HashMap<>();
        for (StockMutationLine line : lines) {
            GmsGoods goods = new GmsGoods();
            goods.setId(line.getGoodsId());
            goods.setName(line.getGoodsName());
            goods.setIsCombo(Boolean.TRUE.equals(line.getCombo()) ? 1 : 0);
            goodsMap.put(line.getGoodsId(), goods);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> comboGoodsIds = goodsMap.values().stream()
                .filter(goods -> goods.getIsCombo() != null && goods.getIsCombo() == 1)
                .map(GmsGoods::getId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, List<GmsGoodsCombo>> comboMap = new HashMap<>();
        Map<Long, GmsGoods> subGoodsMap = new HashMap<>();
        if (!comboGoodsIds.isEmpty()) {
            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(
                    new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, comboGoodsIds));
            comboMap = combos.stream().collect(Collectors.groupingBy(GmsGoodsCombo::getComboGoodsId));
            List<Long> subGoodsIds = combos.stream().map(GmsGoodsCombo::getSubGoodsId).distinct().collect(Collectors.toList());
            if (!subGoodsIds.isEmpty()) {
                subGoodsMap = gmsGoodsMapper.selectBatchIds(subGoodsIds).stream()
                        .collect(Collectors.toMap(GmsGoods::getId, goods -> goods));
            }
        }

        List<GmsStockLog> pendingLogs = new ArrayList<>();
        for (StockMutationLine line : lines) {
            GmsGoods goods = goodsMap.get(line.getGoodsId());
            if (goods == null) {
                throw new BaseException("【库存拦截】商品数据丢失，ID: " + line.getGoodsId());
            }
            if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
                deductCombo(command.getOrderNo(), line, goods, comboMap, subGoodsMap, pendingLogs, now);
            } else {
                deductOne(command.getOrderNo(), goods, line.getQuantity(), "收银台售出", pendingLogs, now);
            }
        }
        saveSaleLogs(pendingLogs);
        createSaleOutDocument(command);
    }

    @Override
    public BigDecimal handle(RefundStockCommand command) {
        GmsInventoryDoc doc = createReturnDoc();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockMutationLine line : command.getLines()) {
            totalCost = totalCost.add(restoreLine(command.getOrderNo(), line, doc));
        }
        doc.setTotalAmount(totalCost);
        inventoryDocMapper.updateById(doc);
        return totalCost;
    }

    private void deductCombo(
            String orderNo, StockMutationLine line, GmsGoods goods,
            Map<Long, List<GmsGoodsCombo>> comboMap, Map<Long, GmsGoods> subGoodsMap,
            List<GmsStockLog> pendingLogs, LocalDateTime now) {
        List<GmsGoodsCombo> combos = comboMap.get(goods.getId());
        if (combos == null || combos.isEmpty()) {
            throw new BaseException("【库存拦截】套餐商品未配置子明细: " + goods.getName());
        }
        int comboRows = gmsGoodsMapper.deductStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
        if (comboRows == 0) {
            throw new BaseException("【库存不足】套餐「" + goods.getName() + "」可售配额不足");
        }
        pendingLogs.add(buildLog(goods, -line.getQuantity(), orderNo, "售出扣除套餐配额", now));

        for (GmsGoodsCombo combo : combos) {
            GmsGoods subGoods = subGoodsMap.get(combo.getSubGoodsId());
            if (subGoods == null) {
                throw new BaseException("【数据异常】套餐子商品不存在: " + combo.getSubGoodsId());
            }
            int deductQty;
            try {
                deductQty = Math.multiplyExact(line.getQuantity(), combo.getSubGoodsQty());
            } catch (ArithmeticException exception) {
                throw new BaseException(String.format("【库存拦截】套餐子商品「%s」扣减总数超出系统安全上限", subGoods.getName()));
            }
            deductOne(orderNo, subGoods, deductQty, "套餐售出联动扣除实物", pendingLogs, now);
        }
    }

    private void deductOne(
            String orderNo, GmsGoods goods, int quantity, String remark,
            List<GmsStockLog> pendingLogs, LocalDateTime now) {
        int rows = gmsGoodsMapper.deductStockAtomically(goods.getId(), new BigDecimal(quantity));
        if (rows == 0) {
            throw new BaseException("【库存不足】商品「" + goods.getName() + "」抢购失败");
        }
        pendingLogs.add(buildLog(goods, -quantity, orderNo, remark, now));
    }

    private void saveSaleLogs(List<GmsStockLog> pendingLogs) {
        if (pendingLogs.isEmpty()) {
            return;
        }
        List<Long> goodsIds = pendingLogs.stream().map(GmsStockLog::getGoodsId).distinct().collect(Collectors.toList());
        Map<Long, GmsGoods> latestGoods = gmsGoodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(GmsGoods::getId, goods -> goods));
        for (GmsStockLog log : pendingLogs) {
            GmsGoods latest = latestGoods.get(log.getGoodsId());
            log.setAfterQuantity(latest != null && latest.getStock() != null ? latest.getStock().intValue() : 0);
        }
        gmsStockLogService.saveBatch(pendingLogs);
    }

    private void createSaleOutDocument(SaleStockCommand command) {
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("XS-" + command.getOrderNo());
        doc.setDocType("SALE_OUT");
        doc.setRemark("front desk settlement, order: " + command.getOrderNo());
        doc.setCreateTime(LocalDateTime.now());

        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockMutationLine line : command.getLines()) {
            BigDecimal quantity = new BigDecimal(line.getQuantity());
            BigDecimal cost = line.getPurchasePrice() == null ? BigDecimal.ZERO : line.getPurchasePrice();
            totalCost = totalCost.add(cost.multiply(quantity));
        }
        doc.setTotalAmount(totalCost.negate());
        inventoryDocMapper.insert(doc);

        for (StockMutationLine line : command.getLines()) {
            GmsInventoryDocItem item = new GmsInventoryDocItem();
            item.setDocNo(doc.getDocNo());
            item.setGoodsId(line.getGoodsId());
            item.setGoodsName(line.getGoodsName());
            item.setBarcode(line.getGoodsBarcode());
            item.setChangeQty(-line.getQuantity());
            item.setCostPrice(line.getPurchasePrice() == null ? BigDecimal.ZERO : line.getPurchasePrice());
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

    private BigDecimal restoreLine(String orderNo, StockMutationLine line, GmsInventoryDoc doc) {
        GmsGoods goods = gmsGoodsMapper.selectById(line.getGoodsId());
        if (goods == null) {
            throw new BaseException("goods not found: " + line.getGoodsId());
        }

        BigDecimal impact = BigDecimal.ZERO;
        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
            int latestComboStock = latestStock(goods.getId());
            recordReturnLog(goods, line.getQuantity(), latestComboStock, orderNo, BigDecimal.ZERO, "refund combo allocation");
            saveDocItem(doc.getDocNo(), goods, line.getQuantity(), BigDecimal.ZERO, latestComboStock);

            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>()
                    .eq(GmsGoodsCombo::getComboGoodsId, goods.getId()));
            for (GmsGoodsCombo combo : combos) {
                GmsGoods sub = gmsGoodsMapper.selectById(combo.getSubGoodsId());
                if (sub == null) {
                    continue;
                }
                int quantity = Math.multiplyExact(line.getQuantity(), combo.getSubGoodsQty());
                gmsGoodsMapper.addStockAtomically(sub.getId(), new BigDecimal(quantity));
                int latestStock = latestStock(sub.getId());
                BigDecimal cost = sub.getAvgCostPrice() != null ? sub.getAvgCostPrice()
                        : (sub.getPurchasePrice() != null ? sub.getPurchasePrice() : BigDecimal.ZERO);
                impact = impact.add(cost.multiply(new BigDecimal(quantity)));
                recordReturnLog(sub, quantity, latestStock, orderNo, cost, "refund combo physical stock");
                saveDocItem(doc.getDocNo(), sub, quantity, cost, latestStock);
            }
        } else {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
            int latestStock = latestStock(goods.getId());
            BigDecimal cost = line.getPurchasePrice() == null ? BigDecimal.ZERO : line.getPurchasePrice();
            impact = cost.multiply(new BigDecimal(line.getQuantity()));
            recordReturnLog(goods, line.getQuantity(), latestStock, orderNo, cost, "refund goods stock");
            saveDocItem(doc.getDocNo(), goods, line.getQuantity(), cost, latestStock);
        }
        return impact;
    }

    private int latestStock(Long goodsId) {
        GmsGoods goods = gmsGoodsMapper.selectById(goodsId);
        return goods != null && goods.getStock() != null ? goods.getStock().intValue() : 0;
    }

    private void saveDocItem(String docNo, GmsGoods goods, int quantity, BigDecimal cost, int latestStock) {
        GmsInventoryDocItem item = new GmsInventoryDocItem();
        item.setDocNo(docNo);
        item.setGoodsId(goods.getId());
        item.setGoodsName(goods.getName());
        item.setBarcode(goods.getBarcode());
        item.setChangeQty(quantity);
        item.setCostPrice(cost);
        item.setPreStock((long) (latestStock - quantity));
        item.setAfterStock((long) latestStock);
        inventoryDocItemMapper.insert(item);
    }

    private GmsStockLog buildLog(GmsGoods goods, int quantity, String orderNo, String remark, LocalDateTime now) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(goods.getId());
        log.setGoodsName(goods.getName());
        log.setGoodsBarcode(goods.getBarcode());
        log.setType("SALE");
        log.setQuantity(quantity);
        log.setOrderNo(orderNo);
        log.setRemark(remark);
        log.setCreateTime(now);
        return log;
    }

    private void recordReturnLog(
            GmsGoods goods, int quantity, int latestStock, String orderNo, BigDecimal cost, String remark) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(goods.getId());
        log.setGoodsName(goods.getName());
        log.setGoodsBarcode(goods.getBarcode());
        log.setType("RETURN");
        log.setQuantity(quantity);
        log.setAfterQuantity(latestStock);
        log.setOrderNo(orderNo);
        log.setCostPriceSnapshot(cost);
        log.setImpactAmount(cost.multiply(new BigDecimal(quantity)));
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        gmsStockLogService.save(log);
    }
}
