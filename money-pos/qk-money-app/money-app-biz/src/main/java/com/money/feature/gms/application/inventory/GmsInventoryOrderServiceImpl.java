package com.money.feature.gms.application.inventory;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.dto.inventory.GmsInventoryOrderDTO;
import com.money.dto.inventory.GmsInventoryOrderDetailDTO;
import com.money.entity.GmsGoods;
import com.money.entity.GmsInventoryOrder;
import com.money.entity.GmsInventoryOrderDetail;
import com.money.entity.GmsStockLog;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsInventoryOrderDetailMapper;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsInventoryOrderMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsStockLogMapper;
import com.money.util.MoneyUtil;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GmsInventoryOrderServiceImpl extends ServiceImpl<GmsInventoryOrderMapper, GmsInventoryOrder> implements GmsInventoryOrderService {

    private final GmsInventoryOrderDetailMapper detailMapper;
    private final GmsGoodsMapper goodsMapper;
    private final GmsStockLogMapper gmsStockLogMapper;

    // 单号生成器
    private String generateOrderNo(String prefix) {
        return prefix + DateUtil.format(new Date(), "yyyyMMddHHmmssSSS") + RandomUtil.randomNumbers(4);
    }

    // 计算总金额 (统一精度防丢失)
    private BigDecimal calculateTotalAmount(List<GmsInventoryOrderDetailDTO> details) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (details != null) {
            for (GmsInventoryOrderDetailDTO detail : details) {
                if (detail.getPrice() != null && detail.getQty() != null) {
                    BigDecimal rowTotal = MoneyUtil.multiply(detail.getPrice(), new BigDecimal(detail.getQty()));
                    totalAmount = MoneyUtil.add(totalAmount, rowTotal);
                }
            }
        }
        return totalAmount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createInboundOrder(GmsInventoryOrderDTO dto) {
        String orderNo = generateOrderNo("IN");
        BigDecimal totalAmount = calculateTotalAmount(dto.getDetails());

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("INBOUND");
        order.setTotalAmount(totalAmount);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            Set<Long> goodsIds = dto.getDetails().stream().map(GmsInventoryOrderDetailDTO::getGoodsId).collect(Collectors.toSet());
            Map<Long, GmsGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

            List<GmsInventoryOrderDetail> detailList = new ArrayList<>();
            List<GmsStockLog> logList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                if (detailDTO.getPrice() == null || detailDTO.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BaseException("入库单价必须大于0，以确保财务成本精准核算！");
                }

                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(detailDTO.getPrice());
                detailList.add(detail);

                GmsGoods goods = goodsMap.get(detailDTO.getGoodsId());
                if (goods != null) {
                    long oldStock = goods.getStock() == null ? 0 : goods.getStock();
                    BigDecimal oldAvgCost = goods.getAvgCostPrice() == null ? BigDecimal.ZERO : goods.getAvgCostPrice();
                    int inQty = detailDTO.getQty();
                    BigDecimal inPrice = detailDTO.getPrice();

                    // 1. 移动加权平均成本 (WMA) 算法
                    BigDecimal newAvgCost;
                    if (oldStock <= 0) {
                        newAvgCost = inPrice;
                    } else {
                        BigDecimal oldTotalValue = MoneyUtil.multiply(oldAvgCost, new BigDecimal(oldStock));
                        BigDecimal inTotalValue = MoneyUtil.multiply(inPrice, new BigDecimal(inQty));
                        BigDecimal totalValue = MoneyUtil.add(oldTotalValue, inTotalValue);
                        long newTotalQty = oldStock + inQty;
                        newAvgCost = totalValue.divide(new BigDecimal(newTotalQty), 2, RoundingMode.HALF_UP);
                    }

                    // 2. 更新主表 (数量 + 双轴成本 + 🌟旧字段双写兼容)
                    LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(GmsGoods::getId, goods.getId());
                    updateWrapper.setSql("stock = stock + " + inQty);
                    updateWrapper.set(GmsGoods::getAvgCostPrice, newAvgCost); // 赋能新财务基准
                    updateWrapper.set(GmsGoods::getLastPurchasePrice, inPrice); // 记录末次采购

                    // 🌟 核心救火：强制覆写旧的 purchasePrice 字段，让不改代码的旧版 POS 收银台也能吃到最新的均价红利！
                    updateWrapper.set(GmsGoods::getPurchasePrice, newAvgCost);

                    if (!"FORBIDDEN".equals(goods.getStatus())) {
                        updateWrapper.set(GmsGoods::getStatus, "SALE");
                    }
                    goodsMapper.update(null, updateWrapper);

                    // 3. 入库流水财务显性化
                    GmsStockLog stockLog = new GmsStockLog();
                    stockLog.setGoodsId(goods.getId());
                    stockLog.setGoodsName(goods.getName());
                    stockLog.setGoodsBarcode(goods.getBarcode());
                    stockLog.setType("INBOUND");
                    stockLog.setQuantity(inQty);
                    stockLog.setAfterQuantity((int) (oldStock + inQty));

                    stockLog.setCostPriceSnapshot(inPrice);
                    stockLog.setImpactAmount(MoneyUtil.multiply(inPrice, new BigDecimal(inQty)));

                    stockLog.setOrderNo(orderNo);
                    stockLog.setRemark("采购入库(重算均价)" + (StrUtil.isNotBlank(dto.getRemark()) ? "：" + dto.getRemark() : ""));
                    stockLog.setCreateTime(now);
                    logList.add(stockLog);
                }
            }
            for (GmsInventoryOrderDetail d : detailList) detailMapper.insert(d);
            for (GmsStockLog l : logList) gmsStockLogMapper.insert(l);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCheckOrder(GmsInventoryOrderDTO dto) {
        String orderNo = generateOrderNo("CH");

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("CHECK");
        order.setTotalAmount(BigDecimal.ZERO);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            Set<Long> goodsIds = dto.getDetails().stream().map(GmsInventoryOrderDetailDTO::getGoodsId).collect(Collectors.toSet());
            Map<Long, GmsGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

            List<GmsInventoryOrderDetail> detailList = new ArrayList<>();
            List<GmsStockLog> logList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(BigDecimal.ZERO);
                detailList.add(detail);

                GmsGoods goods = goodsMap.get(detailDTO.getGoodsId());
                if (goods != null) {
                    long oldStock = goods.getStock() == null ? 0 : goods.getStock();
                    long newStock = detailDTO.getQty();
                    long diff = newStock - oldStock; // 差值

                    if (diff != 0) {
                        // 提取当前的财务基准成本
                        BigDecimal currentAvgCost = goods.getAvgCostPrice() == null ? BigDecimal.ZERO : goods.getAvgCostPrice();

                        LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<>();
                        updateWrapper.eq(GmsGoods::getId, goods.getId());
                        updateWrapper.setSql("stock = stock + " + diff);
                        goodsMapper.update(null, updateWrapper);

                        // 🌟 核心战果 3：盘点损益精准显性化
                        GmsStockLog stockLog = new GmsStockLog();
                        stockLog.setGoodsId(goods.getId());
                        stockLog.setGoodsName(goods.getName());
                        stockLog.setGoodsBarcode(goods.getBarcode());
                        stockLog.setType("CHECK");
                        stockLog.setQuantity((int) diff);
                        stockLog.setAfterQuantity((int) (oldStock + diff));

                        // 快照为【当前均价】，资产影响 = 差量 * 均价 (盘盈为正，盘亏为负)
                        stockLog.setCostPriceSnapshot(currentAvgCost);
                        stockLog.setImpactAmount(MoneyUtil.multiply(currentAvgCost, new BigDecimal(diff)));

                        stockLog.setOrderNo(orderNo);
                        stockLog.setRemark(diff > 0 ? "盘点校准(资产盘盈)" : "盘点校准(资产盘亏)");
                        stockLog.setCreateTime(now);
                        logList.add(stockLog);
                    }
                }
            }
            for (GmsInventoryOrderDetail d : detailList) detailMapper.insert(d);
            for (GmsStockLog l : logList) gmsStockLogMapper.insert(l);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOutboundOrder(GmsInventoryOrderDTO dto) {
        String orderNo = generateOrderNo("OU");
        BigDecimal totalAmount = calculateTotalAmount(dto.getDetails());

        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo(orderNo);
        order.setType("OUTBOUND");
        order.setTotalAmount(totalAmount);
        order.setStatus("COMPLETED");
        order.setRemark(dto.getRemark());
        this.save(order);

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            Set<Long> goodsIds = dto.getDetails().stream().map(GmsInventoryOrderDetailDTO::getGoodsId).collect(Collectors.toSet());
            Map<Long, GmsGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

            List<GmsInventoryOrderDetail> detailList = new ArrayList<>();
            List<GmsStockLog> logList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (GmsInventoryOrderDetailDTO detailDTO : dto.getDetails()) {
                GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
                detail.setOrderId(order.getId());
                detail.setGoodsId(detailDTO.getGoodsId());
                detail.setQty(detailDTO.getQty());
                detail.setPrice(detailDTO.getPrice());
                detailList.add(detail);

                GmsGoods goods = goodsMap.get(detailDTO.getGoodsId());
                if (goods != null) {
                    int scrapQty = detailDTO.getQty();
                    BigDecimal currentAvgCost = goods.getAvgCostPrice() == null ? BigDecimal.ZERO : goods.getAvgCostPrice();

                    LambdaUpdateWrapper<GmsGoods> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(GmsGoods::getId, goods.getId());
                    updateWrapper.setSql("stock = stock - " + scrapQty);
                    goodsMapper.update(null, updateWrapper);

                    // 🌟 核心战果 4：报损资产流失核算
                    GmsStockLog stockLog = new GmsStockLog();
                    stockLog.setGoodsId(goods.getId());
                    stockLog.setGoodsName(goods.getName());
                    stockLog.setGoodsBarcode(goods.getBarcode());
                    stockLog.setType("SCRAP");
                    stockLog.setQuantity(-scrapQty);

                    long oldStock = goods.getStock() == null ? 0 : goods.getStock();
                    stockLog.setAfterQuantity((int) (oldStock - scrapQty));

                    // 快照为【当前均价】，资产影响 = 报损量 * 均价 * -1 (资产绝对减少)
                    stockLog.setCostPriceSnapshot(currentAvgCost);
                    stockLog.setImpactAmount(MoneyUtil.multiply(currentAvgCost, new BigDecimal(-scrapQty)));

                    stockLog.setOrderNo(orderNo);
                    stockLog.setRemark("报损出库(资产流失)" + (StrUtil.isNotBlank(dto.getRemark()) ? "：" + dto.getRemark() : ""));
                    stockLog.setCreateTime(now);
                    logList.add(stockLog);
                }
            }
            for (GmsInventoryOrderDetail d : detailList) detailMapper.insert(d);
            for (GmsStockLog l : logList) gmsStockLogMapper.insert(l);
        }
    }
}
