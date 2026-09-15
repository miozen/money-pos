package com.money.feature.gms.application.stockanalysis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.dto.GmsGoods.GmsStockDataVO.StockAnalysisReportVO;
import com.money.entity.GmsGoods;
import com.money.entity.GmsStockLog;
import com.money.mapper.GmsStockLogMapper;
import com.money.service.GmsGoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GmsStockAnalysisServiceImpl implements GmsStockAnalysisService {

    private final GmsStockLogMapper gmsStockLogMapper;
    private final GmsGoodsService gmsGoodsService;

    @Override
    public List<StockAnalysisReportVO> getStockAnalysisReport(String startDate, String endDate, String keyword) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(29);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        QueryWrapper<GmsStockLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(
                "goods_id AS goodsId",
                "MAX(goods_name) AS goodsName",
                "MAX(goods_barcode) AS goodsBarcode",
                "SUM(CASE WHEN type = 'INBOUND' THEN ABS(quantity) ELSE 0 END) AS inboundQty",
                "SUM(CASE WHEN type = 'RETURN' THEN ABS(quantity) ELSE 0 END) AS returnQty",
                "SUM(CASE WHEN type = 'SALE' THEN ABS(quantity) ELSE 0 END) AS saleQty",
                "SUM(CASE WHEN type = 'SCRAP' THEN ABS(quantity) ELSE 0 END) AS scrapQty",
                "SUM(CASE WHEN type = 'CHECK' THEN quantity ELSE 0 END) AS checkQty",
                "SUM(quantity) AS netChangeQty"
        );

        queryWrapper.ge("create_time", startTime).le("create_time", endTime).isNotNull("goods_id");

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(w -> w.like("goods_name", keyword).or().like("goods_barcode", keyword));
        }

        queryWrapper.groupBy("goods_id");
        List<Map<String, Object>> mapList = gmsStockLogMapper.selectMaps(queryWrapper);

        if (mapList == null || mapList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> goodsIds = mapList.stream().map(map -> Long.valueOf(map.get("goodsId").toString())).collect(Collectors.toList());
        Map<Long, GmsGoods> goodsMap = new HashMap<>();
        if (!goodsIds.isEmpty()) {
            List<GmsGoods> goodsList = gmsGoodsService.listByIds(goodsIds);
            goodsMap = goodsList.stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));
        }

        List<StockAnalysisReportVO> resultList = new ArrayList<>(mapList.size());

        for (Map<String, Object> map : mapList) {
            StockAnalysisReportVO vo = new StockAnalysisReportVO();
            Long goodsId = Long.valueOf(map.get("goodsId").toString());

            vo.setGoodsId(goodsId);
            vo.setGoodsName(map.get("goodsName") != null ? map.get("goodsName").toString() : "未知");
            vo.setGoodsBarcode(map.get("goodsBarcode") != null ? map.get("goodsBarcode").toString() : "");

            // 🌟 核心防雷修复：使用 new BigDecimal().intValue()，完美兼容 "5" 和 "5.000" 的字符串转换！
            vo.setInboundQty(new BigDecimal(map.get("inboundQty").toString()).intValue());
            vo.setReturnQty(new BigDecimal(map.get("returnQty").toString()).intValue());
            vo.setSaleQty(new BigDecimal(map.get("saleQty").toString()).intValue());
            vo.setScrapQty(new BigDecimal(map.get("scrapQty").toString()).intValue());
            vo.setCheckQty(new BigDecimal(map.get("checkQty").toString()).intValue());
            vo.setNetChangeQty(new BigDecimal(map.get("netChangeQty").toString()).intValue());

            BigDecimal purchasePrice = BigDecimal.ZERO;
            GmsGoods goods = goodsMap.get(goodsId);
            if (goods != null && goods.getPurchasePrice() != null) {
                purchasePrice = goods.getPurchasePrice();
                vo.setPurchasePrice(purchasePrice);
            }

            int totalLossQty = vo.getScrapQty();
            if (vo.getCheckQty() < 0) {
                totalLossQty += Math.abs(vo.getCheckQty());
            }
            vo.setLossAmount(purchasePrice.multiply(new BigDecimal(totalLossQty)));

            resultList.add(vo);
        }

        resultList.sort((a, b) -> b.getNetChangeQty().compareTo(a.getNetChangeQty()));
        return resultList;
    }
}
