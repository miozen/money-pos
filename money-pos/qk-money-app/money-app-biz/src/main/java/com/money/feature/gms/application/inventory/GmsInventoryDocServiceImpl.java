package com.money.feature.gms.application.inventory;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.dto.GmsGoods.InventoryDocRequestDTO;
import com.money.entity.GmsGoods;
import com.money.entity.GmsInventoryDoc;
import com.money.entity.GmsInventoryDocItem;
import com.money.entity.GmsStockLog;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocItemMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.GmsStockLogMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmsInventoryDocServiceImpl implements GmsInventoryDocService {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsInventoryDocMapper gmsInventoryDocMapper;
    private final GmsInventoryDocItemMapper gmsInventoryDocItemMapper;
    private final GmsStockLogMapper gmsStockLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeDoc(InventoryDocRequestDTO requestDTO) {
        if (requestDTO.getDetails() == null || requestDTO.getDetails().isEmpty()) {
            throw new BaseException("单据明细不能为空");
        }

        String docType = requestDTO.getType();
        LocalDateTime now = LocalDateTime.now();

        // 1. 智能分配单号前缀
        String prefix = "QT";
        if ("INBOUND".equals(docType)) prefix = "RK";       // 采购入库
        else if ("CHECK".equals(docType)) prefix = "PD";    // 盘点
        else if ("OUTBOUND".equals(docType)) prefix = "BS"; // 报损出库

        String docNo = prefix + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + RandomUtil.randomNumbers(4);

        // 2. 获取当前操作人
        String currentOperator = "System";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            currentOperator = auth.getName();
        }

        int totalQtyAbsolute = 0; // 影响的总件数(绝对值)
        BigDecimal totalFinancialImpact = BigDecimal.ZERO; // 财务总影响(入库为正数进货额，报损为负数亏损额，盘点有正有负)

        List<GmsInventoryDocItem> snapshotItems = new ArrayList<>();
        List<GmsStockLog> logItems = new ArrayList<>();

        // 3. 核心循环：处理每个商品的变动逻辑
        for (InventoryDocRequestDTO.ItemDTO item : requestDTO.getDetails()) {
            if (item.getQty() == null) continue;

            GmsGoods goods = gmsGoodsMapper.selectById(item.getGoodsId());
            if (goods == null) throw new BaseException("商品不存在，ID: " + item.getGoodsId());

            long preStock = goods.getStock() != null ? goods.getStock() : 0L;
            BigDecimal preAvgPrice = goods.getAvgCostPrice() != null ? goods.getAvgCostPrice() : BigDecimal.ZERO;

            int changeQty = 0;
            BigDecimal actionCostPrice = BigDecimal.ZERO;
            BigDecimal newAvgCostPrice = preAvgPrice; // 默认均价不变

            // 🌟 核心策略分发：计算【真实变动量】与【快照成本价】
            if ("INBOUND".equals(docType)) {
                if (item.getQty() <= 0) continue;
                if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BaseException("入库单价异常: " + goods.getName());
                }
                changeQty = item.getQty(); // 纯增加
                actionCostPrice = item.getPrice(); // 成本为手动录入的进价

                // 💰 触发移动加权平均核算
                if (preStock > 0) {
                    BigDecimal oldTotalValue = preAvgPrice.multiply(new BigDecimal(preStock));
                    BigDecimal newInValue = actionCostPrice.multiply(new BigDecimal(changeQty));
                    BigDecimal totalStock = new BigDecimal(preStock + changeQty);
                    newAvgCostPrice = oldTotalValue.add(newInValue).divide(totalStock, 2, RoundingMode.HALF_UP);
                } else {
                    newAvgCostPrice = actionCostPrice; // 如果之前没库存，新均价就是本次进价
                }
            }
            else if ("OUTBOUND".equals(docType)) {
                if (item.getQty() <= 0) continue;
                changeQty = -item.getQty(); // 纯扣减
                actionCostPrice = preAvgPrice; // 报损损失按照【当前均价】核算
            }
            else if ("CHECK".equals(docType)) {
                // 盘点模式下，前端传的是“货架实际数量”
                changeQty = (int) (item.getQty() - preStock); // 差值：多退少补
                if (changeQty == 0) continue; // 没差异跳过
                actionCostPrice = preAvgPrice; // 盘盈盘亏均按照【当前均价】核算
            }

            if (changeQty == 0) continue; // 拦截无效操作

            // 防护：只有报损才检查库存是否不足（盘点如果数量为0，会自动生成负数扣平）
            if ("OUTBOUND".equals(docType) && preStock + changeQty < 0) {
                throw new BaseException(goods.getName() + " 当前库存不足，无法报损！");
            }

            // 4. 更新商品主表
            LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<GmsGoods>()
                    .eq(GmsGoods::getId, goods.getId())
                    .setSql("stock = stock + " + changeQty);

            // 只有入库才需要改变成本价
            if ("INBOUND".equals(docType)) {
                updateWrapper.set(GmsGoods::getAvgCostPrice, newAvgCostPrice)
                        .set(GmsGoods::getLastPurchasePrice, actionCostPrice);
            }
            gmsGoodsMapper.update(null, updateWrapper);

            // 5. 记录单据快照 (The Snapshot)
            GmsInventoryDocItem snapshot = new GmsInventoryDocItem();
            snapshot.setDocNo(docNo);
            snapshot.setGoodsId(goods.getId());
            snapshot.setGoodsName(goods.getName());
            snapshot.setBarcode(goods.getBarcode());
            snapshot.setChangeQty(changeQty);
            snapshot.setCostPrice(actionCostPrice);
            snapshot.setPreStock(preStock);
            snapshot.setAfterStock(preStock + changeQty);
            snapshotItems.add(snapshot);

            // 6. 记录库存流水 (The Log)
            BigDecimal itemFinancialImpact = actionCostPrice.multiply(new BigDecimal(changeQty));

            GmsStockLog stockLog = new GmsStockLog();
            stockLog.setGoodsId(goods.getId());
            stockLog.setGoodsName(goods.getName());
            stockLog.setGoodsBarcode(goods.getBarcode());
            stockLog.setType(docType);
            stockLog.setQuantity(changeQty);
            stockLog.setAfterQuantity((int)(preStock + changeQty));
            stockLog.setCostPriceSnapshot(actionCostPrice);
            stockLog.setImpactAmount(itemFinancialImpact);
            stockLog.setOrderNo(docNo);
            stockLog.setRemark(requestDTO.getRemark());
            stockLog.setCreateTime(now);
            stockLog.setCreator(currentOperator);
            logItems.add(stockLog);

            // 累计总单据数据
            totalQtyAbsolute += Math.abs(changeQty);
            totalFinancialImpact = totalFinancialImpact.add(itemFinancialImpact);
        }

        if (snapshotItems.isEmpty()) {
            throw new BaseException("单据无实质性数量变化，无需提交！");
        }

        // 7. 落库主单据
        GmsInventoryDoc mainDoc = new GmsInventoryDoc();
        mainDoc.setDocNo(docNo);
        mainDoc.setDocType(docType);
        mainDoc.setTotalQty(totalQtyAbsolute);
        mainDoc.setTotalAmount(totalFinancialImpact);
        mainDoc.setOperator(currentOperator);
        mainDoc.setRemark(requestDTO.getRemark());
        gmsInventoryDocMapper.insert(mainDoc);

        // 8. 批量落库明细与流水
        for (GmsInventoryDocItem snapshotItem : snapshotItems) gmsInventoryDocItemMapper.insert(snapshotItem);
        for (GmsStockLog logItem : logItems) gmsStockLogMapper.insert(logItem);

        log.info("✅ 库存单据引擎处理完毕！单号: {}, 类型: {}, 总额变化: {}", docNo, docType, totalFinancialImpact);
    }
}
