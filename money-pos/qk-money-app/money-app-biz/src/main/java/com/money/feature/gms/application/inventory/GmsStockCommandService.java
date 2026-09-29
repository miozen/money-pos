package com.money.feature.gms.application.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.contract.goods.MemberPickupStockCommand;
import com.money.contract.goods.MemberPickupStockCommandHandler;
import com.money.contract.goods.MemberPickupReturnStockCommand;
import com.money.contract.goods.MemberPickupReturnStockCommandHandler;
import com.money.contract.goods.RefundStockCommand;
import com.money.contract.goods.RefundStockCommandHandler;
import com.money.contract.goods.SaleStockCommand;
import com.money.contract.goods.SaleStockCommandHandler;
import com.money.contract.goods.StockMutationLine;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCombo;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDocItem;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocItemMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.service.GmsStockLogService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
class GmsStockCommandService implements SaleStockCommandHandler, RefundStockCommandHandler, MemberPickupStockCommandHandler,
        MemberPickupReturnStockCommandHandler {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsComboMapper gmsGoodsComboMapper;
    private final GmsStockLogService gmsStockLogService;
    private final GmsInventoryDocMapper inventoryDocMapper;
    private final GmsInventoryDocItemMapper inventoryDocItemMapper;

    @Override
    public void handle(SaleStockCommand command) {
        deductStockMutation(command.getOrderNo(), command.getLines(), StockMutationSemantic.sale());
    }

    /**
     * A physical pickup follows the same stock-locking and combo-expansion algorithm as a sale,
     * but is classified independently so it never contributes to normal SALE / SALE_OUT reporting.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(MemberPickupStockCommand command) {
        if (command == null || command.getPickupNo() == null || command.getLines() == null || command.getLines().isEmpty()) {
            throw new BaseException("会员提货库存命令不完整");
        }
        deductStockMutation(command.getPickupNo(), command.getLines(), StockMutationSemantic.memberPickup());
    }

    /**
     * Shared physical stock mutation path.  The caller supplies the business semantic; it never
     * performs a SALE then rewrites its records for member pickup.
     */
    private void deductStockMutation(String referenceNo, List<StockMutationLine> lines, StockMutationSemantic semantic) {
        Map<Long, GmsGoods> goodsMap = new HashMap<>();
        for (StockMutationLine line : lines) {
            GmsGoods goods = new GmsGoods();
            goods.setId(line.getGoodsId());
            goods.setName(line.getGoodsName());
            goods.setIsCombo(Boolean.TRUE.equals(line.getCombo()) ? 1 : 0);
            goodsMap.put(line.getGoodsId(), goods);
        }
        List<Long> comboIds = goodsMap.values().stream()
                .filter(goods -> goods.getIsCombo() != null && goods.getIsCombo() == 1)
                .map(GmsGoods::getId).distinct().collect(Collectors.toList());
        Map<Long, List<GmsGoodsCombo>> comboMap = new HashMap<>();
        Map<Long, GmsGoods> subGoodsMap = new HashMap<>();
        if (!comboIds.isEmpty()) {
            List<GmsGoodsCombo> combos = gmsGoodsComboMapper.selectList(
                    new LambdaQueryWrapper<GmsGoodsCombo>().in(GmsGoodsCombo::getComboGoodsId, comboIds));
            comboMap = combos.stream().collect(Collectors.groupingBy(GmsGoodsCombo::getComboGoodsId));
            List<Long> subIds = combos.stream().map(GmsGoodsCombo::getSubGoodsId).distinct().collect(Collectors.toList());
            if (!subIds.isEmpty()) {
                subGoodsMap = gmsGoodsMapper.selectBatchIds(subIds).stream()
                        .collect(Collectors.toMap(GmsGoods::getId, goods -> goods));
            }
        }
        LocalDateTime now = LocalDateTime.now();
        List<GmsStockLog> logs = new ArrayList<>();
        for (StockMutationLine line : lines) {
            GmsGoods goods = goodsMap.get(line.getGoodsId());
            if (goods == null) {
                throw new BaseException("【库存拦截】商品数据丢失，ID: " + line.getGoodsId());
            }
            if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
                deductCombo(referenceNo, line, goods, comboMap, subGoodsMap, semantic, logs, now);
            } else {
                deductOne(referenceNo, goods, line.getQuantity(), semantic.singleRemark, semantic.logType, logs, now);
            }
        }
        saveStockLogs(logs);
        createStockMutationDocument(referenceNo, lines, semantic);
    }

    private void createStockMutationDocument(String referenceNo, List<StockMutationLine> lines, StockMutationSemantic semantic) {
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo(semantic.documentPrefix + referenceNo);
        doc.setDocType(semantic.documentType);
        doc.setRemark(semantic.documentRemark + referenceNo);
        doc.setCreateTime(LocalDateTime.now());
        BigDecimal total = BigDecimal.ZERO;
        for (StockMutationLine line : lines) {
            total = total.add((line.getPurchasePrice() == null ? BigDecimal.ZERO : line.getPurchasePrice())
                    .multiply(new BigDecimal(line.getQuantity())));
        }
        doc.setTotalAmount(total.negate());
        inventoryDocMapper.insert(doc);
        for (StockMutationLine line : lines) {
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

    @Override
    public BigDecimal handle(RefundStockCommand command) {
        return restoreStockMutation(command.getOrderNo(), command.getLines(), ReturnSemantic.normal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal handle(MemberPickupReturnStockCommand command) {
        if (command == null || command.getReturnNo() == null || command.getLines() == null || command.getLines().isEmpty()) {
            throw new BaseException("会员提货退货库存命令不完整");
        }
        return restoreStockMutation(command.getReturnNo(), command.getLines(), ReturnSemantic.memberPickupReturn());
    }

    private BigDecimal restoreStockMutation(String referenceNo, List<StockMutationLine> lines, ReturnSemantic semantic) {
        GmsInventoryDoc doc = createReturnDoc(referenceNo, semantic);
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockMutationLine line : lines) {
            totalCost = totalCost.add(restoreLine(referenceNo, line, doc, semantic));
        }
        doc.setTotalAmount(totalCost);
        inventoryDocMapper.updateById(doc);
        return totalCost;
    }

    private void deductCombo(
            String orderNo, StockMutationLine line, GmsGoods goods,
            Map<Long, List<GmsGoodsCombo>> comboMap, Map<Long, GmsGoods> subGoodsMap,
            StockMutationSemantic semantic, List<GmsStockLog> pendingLogs, LocalDateTime now) {
        List<GmsGoodsCombo> combos = comboMap.get(goods.getId());
        if (combos == null || combos.isEmpty()) {
            throw new BaseException("【库存拦截】套餐商品未配置子明细: " + goods.getName());
        }
        int comboRows = gmsGoodsMapper.deductStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
        if (comboRows == 0) {
            throw new BaseException("【库存不足】套餐「" + goods.getName() + "」可售配额不足");
        }
        pendingLogs.add(buildLog(goods, -line.getQuantity(), orderNo, semantic.comboAllocationRemark, semantic.logType, now));

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
            deductOne(orderNo, subGoods, deductQty, semantic.comboPhysicalRemark, semantic.logType, pendingLogs, now);
        }
    }

    private void deductOne(
            String orderNo, GmsGoods goods, int quantity, String remark, String logType,
            List<GmsStockLog> pendingLogs, LocalDateTime now) {
        int rows = gmsGoodsMapper.deductStockAtomically(goods.getId(), new BigDecimal(quantity));
        if (rows == 0) {
            throw new BaseException("【库存不足】商品「" + goods.getName() + "」抢购失败");
        }
        pendingLogs.add(buildLog(goods, -quantity, orderNo, remark, logType, now));
    }

    private void saveStockLogs(List<GmsStockLog> pendingLogs) {
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

    private GmsInventoryDoc createReturnDoc(String referenceNo, ReturnSemantic semantic) {
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo(semantic.documentPrefix + (semantic.memberPickupReturn ? referenceNo : System.currentTimeMillis()));
        doc.setDocType(semantic.documentType);
        doc.setCreateTime(LocalDateTime.now());
        inventoryDocMapper.insert(doc);
        return doc;
    }

    private BigDecimal restoreLine(String orderNo, StockMutationLine line, GmsInventoryDoc doc, ReturnSemantic semantic) {
        GmsGoods goods = gmsGoodsMapper.selectById(line.getGoodsId());
        if (goods == null) {
            throw new BaseException("goods not found: " + line.getGoodsId());
        }

        BigDecimal impact = BigDecimal.ZERO;
        if (goods.getIsCombo() != null && goods.getIsCombo() == 1) {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
            int latestComboStock = latestStock(goods.getId());
            recordReturnLog(goods, line.getQuantity(), latestComboStock, orderNo, BigDecimal.ZERO, semantic, "refund combo allocation");
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
                recordReturnLog(sub, quantity, latestStock, orderNo, cost, semantic, "refund combo physical stock");
                saveDocItem(doc.getDocNo(), sub, quantity, cost, latestStock);
            }
        } else {
            gmsGoodsMapper.addStockAtomically(goods.getId(), new BigDecimal(line.getQuantity()));
            int latestStock = latestStock(goods.getId());
            BigDecimal cost = line.getPurchasePrice() == null ? BigDecimal.ZERO : line.getPurchasePrice();
            impact = cost.multiply(new BigDecimal(line.getQuantity()));
            recordReturnLog(goods, line.getQuantity(), latestStock, orderNo, cost, semantic, "refund goods stock");
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

    private GmsStockLog buildLog(
            GmsGoods goods, int quantity, String orderNo, String remark, String logType, LocalDateTime now) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(goods.getId());
        log.setGoodsName(goods.getName());
        log.setGoodsBarcode(goods.getBarcode());
        log.setType(logType);
        log.setQuantity(quantity);
        log.setOrderNo(orderNo);
        log.setRemark(remark);
        log.setCreateTime(now);
        return log;
    }

    private static final class StockMutationSemantic {
        private final String logType;
        private final String documentPrefix;
        private final String documentType;
        private final String documentRemark;
        private final String singleRemark;
        private final String comboAllocationRemark;
        private final String comboPhysicalRemark;

        private StockMutationSemantic(String logType, String documentPrefix, String documentType,
                                      String documentRemark, String singleRemark,
                                      String comboAllocationRemark, String comboPhysicalRemark) {
            this.logType = logType;
            this.documentPrefix = documentPrefix;
            this.documentType = documentType;
            this.documentRemark = documentRemark;
            this.singleRemark = singleRemark;
            this.comboAllocationRemark = comboAllocationRemark;
            this.comboPhysicalRemark = comboPhysicalRemark;
        }

        private static StockMutationSemantic sale() {
            return new StockMutationSemantic("SALE", "XS-", "SALE_OUT", "front desk settlement, order: ",
                    "收银台售出", "售出扣除套餐配额", "套餐售出联动扣除实物");
        }

        private static StockMutationSemantic memberPickup() {
            return new StockMutationSemantic("MEMBER_PICKUP", "MP-", "MEMBER_PICKUP", "member deferred pickup: ",
                    "会员权益提货", "会员提货扣除套餐配额", "会员提货套餐实物");
        }
    }

    private static final class ReturnSemantic {
        private final String documentPrefix;
        private final String documentType;
        private final String logType;
        private final boolean memberPickupReturn;

        private ReturnSemantic(String documentPrefix, String documentType, String logType, boolean memberPickupReturn) {
            this.documentPrefix = documentPrefix; this.documentType = documentType;
            this.logType = logType; this.memberPickupReturn = memberPickupReturn;
        }

        private static ReturnSemantic normal() { return new ReturnSemantic("TH", "RETURN", "RETURN", false); }
        private static ReturnSemantic memberPickupReturn() {
            return new ReturnSemantic("MPR-", "MEMBER_PICKUP_RETURN", "MEMBER_PICKUP_RETURN", true);
        }
    }

    private void recordReturnLog(
            GmsGoods goods, int quantity, int latestStock, String orderNo, BigDecimal cost,
            ReturnSemantic semantic, String remark) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(goods.getId());
        log.setGoodsName(goods.getName());
        log.setGoodsBarcode(goods.getBarcode());
        log.setType(semantic.logType);
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
