package com.money.feature.ums.application.member;

import com.alibaba.excel.EasyExcel;
import com.money.contract.goods.BrandSelectionQuery;
import com.money.contract.goods.BrandSelectionSnapshot;
import com.money.service.SysDictDetailService;
import com.money.util.ExcelDropDownHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UMS 会员 Excel 模板的只读输出边界。
 *
 * <p>此服务同时提供导出复用的表头和等级名称映射，确保模板、导出与导入的动态品牌列不漂移。</p>
 */
@Service
@RequiredArgsConstructor
public class UmsMemberExcelTemplateService {

    private final BrandSelectionQuery brandSelectionQuery;
    private final SysDictDetailService sysDictDetailService;

    public void writeTemplate(HttpServletResponse response) throws IOException {
        List<List<String>> heads = buildDynamicHeads();
        configureDownload(response, "智能会员导入模板");

        List<Object> demoRow = new ArrayList<>(Arrays.asList("张老板", "13800138000", "500.00", "50.00", "2"));
        for (int column = 5; column < heads.size(); column++) {
            demoRow.add("");
        }

        EasyExcel.write(response.getOutputStream())
                .head(heads)
                .registerWriteHandler(new ExcelDropDownHandler(buildDropDownConfig(heads.size())))
                .sheet("会员数据填写区")
                .doWrite(List.of(demoRow));
    }

    /**
     * 模板和全量导出共用的动态品牌表头。
     */
    public List<List<String>> buildDynamicHeads() {
        List<List<String>> heads = new ArrayList<>();
        heads.add(List.of("*会员姓名(必填)"));
        heads.add(List.of("*手机号(必填11位)"));
        heads.add(List.of("初始会员余额(本金)"));
        heads.add(List.of("初始会员券(赠送)"));
        heads.add(List.of("初始满减券(张数)"));
        for (BrandSelectionSnapshot brand : brandSelectionQuery.listBrandSelections()) {
            heads.add(List.of("[品牌特权] " + brand.getName()));
        }
        return heads;
    }

    /**
     * 导出时将存储的会员等级 code 翻译为模板使用的中文等级名称。
     */
    public Map<String, String> getMemberTypeNameByCode() {
        Map<String, String> result = new java.util.LinkedHashMap<>(sysDictDetailService.getValueToCnDescMap("memberType"));
        result.remove("MEMBER");
        return result;
    }

    private Map<Integer, String[]> buildDropDownConfig(int totalColumns) {
        String[] levelOptions = getMemberTypeNameByCode().values().toArray(String[]::new);
        Map<Integer, String[]> result = new HashMap<>();
        if (levelOptions.length > 0) {
            for (int column = 5; column < totalColumns; column++) {
                result.put(column, levelOptions);
            }
        }
        return result;
    }

    private void configureDownload(HttpServletResponse response, String filename) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFilename = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encodedFilename + ".xlsx");
    }
}
