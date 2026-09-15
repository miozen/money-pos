package com.money.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.GmsBrand;
import com.money.entity.GmsGoods;
import com.money.entity.GmsGoodsCategory;
import com.money.entity.PosSkuLevelPrice;
import com.money.entity.SysDictDetail;
import com.money.mapper.GmsBrandMapper;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
import com.money.mapper.SysDictDetailMapper;
import com.money.service.GmsGoodsPriceService;
import com.money.service.GmsGoodsService;
import com.money.service.impl.GmsGoodsExcelManager;
import com.money.util.ExcelDropDownHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "gmsGoodsExcel", description = "商品导入导出中心")
@RestController
@RequiredArgsConstructor
public class GmsGoodsExcelController {

    private final GmsGoodsExcelManager gmsGoodsExcelManager;
    private final GmsGoodsCategoryMapper gmsGoodsCategoryMapper;
    private final GmsBrandMapper gmsBrandMapper;
    private final SysDictDetailMapper sysDictDetailMapper;
    private final GmsGoodsService gmsGoodsService;

    // 用于批量拉取 9 级价格矩阵
    private final GmsGoodsPriceService gmsGoodsPriceService;

    @Operation(summary = "Excel智能矩阵批量导入商品")
    @PostMapping("/gms/goods/import")
    public String importGoods(@RequestParam("file") MultipartFile file) {
        return gmsGoodsExcelManager.importGoods(file);
    }

    @GetMapping("/gms/goods/template")
    @Operation(summary = "下载智能商品导入模板")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        List<List<String>> heads = buildDynamicHeads();
        Map<Integer, String[]> dropDownConfig = buildDropDownConfig();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("智能商品导入模板", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        List<List<Object>> demoData = new ArrayList<>();
        // 🌟 核心同步：因为新增了“参与满减”列，这里的示范数据也必须多塞一个空位或者示范值
        List<Object> demoRow = new ArrayList<>(Arrays.asList("690123456789", "农夫山泉500ml", "", "", "上架 (SALE)", "允许", "瓶", "500ml", "2.00", "1.15", "100"));
        for (int i = 0; i < heads.size() - 11; i++) { demoRow.add(""); }
        demoData.add(demoRow);

        EasyExcel.write(response.getOutputStream()).head(heads).registerWriteHandler(new ExcelDropDownHandler(dropDownConfig)).sheet("商品资料填写区").doWrite(demoData);
    }

    @GetMapping("/gms/goods/export")
    @Operation(summary = "导出全量商品资料到 Excel (包含完整价格矩阵)")
    public void exportGoods(HttpServletResponse response) throws IOException {
        List<List<String>> heads = buildDynamicHeads();

        List<GmsGoodsCategory> categories = gmsGoodsCategoryMapper.selectList(null);
        Map<Long, String> catMap = categories.stream().collect(Collectors.toMap(GmsGoodsCategory::getId, GmsGoodsCategory::getName));

        List<GmsBrand> brands = gmsBrandMapper.selectList(null);
        Map<Long, String> brandMap = brands.stream().collect(Collectors.toMap(GmsBrand::getId, GmsBrand::getName));

        List<SysDictDetail> dictList = getVipDictList();

        List<GmsGoods> goodsList = gmsGoodsService.list();

        List<Long> goodsIds = goodsList.stream().map(GmsGoods::getId).collect(Collectors.toList());
        Map<Long, List<PosSkuLevelPrice>> allPriceMatrixMap = new HashMap<>();
        if (!goodsIds.isEmpty()) {
            allPriceMatrixMap = gmsGoodsPriceService.getPriceMap(goodsIds);
        }

        List<List<Object>> dataList = new ArrayList<>();
        for (GmsGoods goods : goodsList) {
            List<Object> row = new ArrayList<>();
            row.add(goods.getBarcode());
            row.add(goods.getName());
            row.add(goods.getCategoryId() != null ? catMap.getOrDefault(goods.getCategoryId(), "") : "");
            row.add(goods.getBrandId() != null ? brandMap.getOrDefault(goods.getBrandId(), "") : "");

            String statusStr = String.valueOf(goods.getStatus()).toUpperCase();
            if ("UP".equals(statusStr) || "1".equals(statusStr) || "TRUE".equals(statusStr) || "SALE".equals(statusStr)) {
                row.add("上架 (SALE)");
            } else {
                row.add("下架 (SOLD_OUT)");
            }

            // 🌟 核心同步：导出时，也必须将数据库里的满减状态翻译为 Excel 的中文字段
            Integer discountStatus = goods.getIsDiscountParticipable();
            if (discountStatus != null && discountStatus == 1) {
                row.add("允许");
            } else {
                row.add("禁止");
            }

            row.add(goods.getUnit());
            row.add(goods.getSize());
            row.add(goods.getSalePrice());
            row.add(goods.getPurchasePrice());
            row.add(goods.getStock());

            List<PosSkuLevelPrice> skuPrices = allPriceMatrixMap.getOrDefault(goods.getId(), new ArrayList<>());
            Map<String, BigDecimal> currentSkuPriceMap = skuPrices.stream()
                    .collect(Collectors.toMap(PosSkuLevelPrice::getLevelId, PosSkuLevelPrice::getMemberPrice, (v1, v2) -> v1));

            for (SysDictDetail dict : dictList) {
                BigDecimal price = currentSkuPriceMap.get(dict.getValue());
                row.add(price != null ? price.toString() : "");
            }

            dataList.add(row);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("门店商品全量档案", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream()).head(heads).sheet("全量商品数据").doWrite(dataList);
    }

    private List<List<String>> buildDynamicHeads() {
        List<List<String>> heads = new ArrayList<>();
        heads.add(Arrays.asList("*商品条码(必填且唯一)"));  // 0
        heads.add(Arrays.asList("*商品名称(必填)"));       // 1
        heads.add(Arrays.asList("所属分类(请选择)"));      // 2
        heads.add(Arrays.asList("所属品牌(请选择)"));      // 3
        heads.add(Arrays.asList("商品状态(请选择)"));      // 4

        // 🌟 核心插入：在第 5 列增加满减配置，且它也会受到下面下拉菜单的约束！
        heads.add(Arrays.asList("参与满减(请选择)"));      // 5

        heads.add(Arrays.asList("单位(如:件)"));           // 6
        heads.add(Arrays.asList("规格(如:500g)"));         // 7
        heads.add(Arrays.asList("建议零售价"));            // 8
        heads.add(Arrays.asList("加权平均成本价"));        // 9
        heads.add(Arrays.asList("初始库存"));              // 10

        List<SysDictDetail> dictList = getVipDictList();
        for (SysDictDetail dict : dictList) {
            heads.add(Arrays.asList("[会员特价] " + dict.getCnDesc()));
        }
        return heads;
    }

    private Map<Integer, String[]> buildDropDownConfig() {
        Map<Integer, String[]> dropDownConfig = new HashMap<>();
        List<GmsGoodsCategory> categories = gmsGoodsCategoryMapper.selectList(new LambdaQueryWrapper<>());
        if (categories != null && !categories.isEmpty()) {
            dropDownConfig.put(2, categories.stream().map(GmsGoodsCategory::getName).toArray(String[]::new));
        }
        List<GmsBrand> brands = gmsBrandMapper.selectList(new LambdaQueryWrapper<>());
        if (brands != null && !brands.isEmpty()) {
            dropDownConfig.put(3, brands.stream().map(GmsBrand::getName).toArray(String[]::new));
        }
        dropDownConfig.put(4, new String[]{"上架 (SALE)", "下架 (SOLD_OUT)"});

        // 🌟 核心联动：给第 5 列（参与满减）加上只允许选“允许”或“禁止”的强制下拉约束
        dropDownConfig.put(5, new String[]{"允许", "禁止"});

        return dropDownConfig;
    }

    private List<SysDictDetail> getVipDictList() {
        return sysDictDetailMapper.selectList(new LambdaQueryWrapper<SysDictDetail>().eq(SysDictDetail::getDict, "memberType").ne(SysDictDetail::getValue, "MEMBER"));
    }
}
