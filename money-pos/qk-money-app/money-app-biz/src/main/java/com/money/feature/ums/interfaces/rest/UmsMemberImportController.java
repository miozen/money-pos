package com.money.feature.ums.interfaces.rest;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.GmsBrand;
import com.money.entity.PosMemberCoupon;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberBrandLevel;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.feature.ums.application.member.UmsMemberExcelTemplateService;
import com.money.feature.ums.application.member.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "umsMemberImport", description = "会员运营导入中心")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberImportController {

    private final UmsMemberService umsMemberService;
    private final UmsMemberExcelTemplateService umsMemberExcelTemplateService;
    private final GmsBrandMapper gmsBrandMapper;

    // 🌟 新增：用于查询全量数据和从表矩阵
    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    @PostMapping("/import")
    @Operation(summary = "从 Excel 批量导入老会员")
    @PreAuthorize("@rbac.hasPermission('umsMember:add')")
    public String importMembers(@RequestPart("file") MultipartFile file) {
        return umsMemberService.importMembers(file);
    }

    @PostMapping("/batch-issue-voucher")
    @Operation(summary = "批量发放满减券")
    @PreAuthorize("@rbac.hasPermission('umsMember:edit')")
    public void batchIssueVoucher(@RequestBody com.money.dto.Ums.BatchIssueVoucherDTO dto) {
        umsMemberService.batchIssueVoucher(dto.getMemberIds(), dto.getRuleId(), dto.getQuantity());
    }

    @GetMapping("/template")
    @Operation(summary = "下载智能老会员导入模板 (动态品牌列+下拉框)")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        umsMemberExcelTemplateService.writeTemplate(response);
    }

    // ==========================================
    // 🌟 新增：一键导出全量真实会员档案与品牌矩阵
    // ==========================================
    @GetMapping("/export")
    @Operation(summary = "一键导出全量老会员资产与矩阵")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public void exportMembers(HttpServletResponse response) throws IOException {
        // 1. 获取动态表头 (与导入模板严格对齐)
        List<List<String>> heads = umsMemberExcelTemplateService.buildDynamicHeads();

        // 2. 查出所有的品牌，用于表头对应
        List<GmsBrand> brands = gmsBrandMapper.selectList(new LambdaQueryWrapper<GmsBrand>());

        // 3. 查出会员等级字典，做 code -> cnDesc 的逆向翻译
        Map<String, String> levelCodeToNameMap = umsMemberExcelTemplateService.getMemberTypeNameByCode();

        // 4. 查出所有的会员主表数据
        List<UmsMember> allMembers = umsMemberMapper.selectList(new LambdaQueryWrapper<UmsMember>());

        // 如果没有会员，直接返回空表
        if (allMembers == null || allMembers.isEmpty()) {
            writeExcelResponse(response, heads, new ArrayList<>());
            return;
        }

        List<Long> memberIds = allMembers.stream().map(UmsMember::getId).collect(Collectors.toList());

        // 🌟 5. 极速查询：批量拉取所有会员的【满减券】和【品牌等级矩阵】

        // 5.1 满减券数量统计 (过滤出未使用的)
        List<PosMemberCoupon> allCoupons = posMemberCouponMapper.selectList(
                new LambdaQueryWrapper<PosMemberCoupon>()
                        .in(PosMemberCoupon::getMemberId, memberIds)
                        .eq(PosMemberCoupon::getStatus, "UNUSED")
        );
        Map<Long, Long> memberVoucherCountMap = allCoupons.stream()
                .collect(Collectors.groupingBy(PosMemberCoupon::getMemberId, Collectors.counting()));

        // 5.2 品牌等级矩阵映射 Map<MemberId, Map<BrandId, LevelCode>>
        List<UmsMemberBrandLevel> allBrandLevels = umsMemberBrandLevelMapper.selectList(
                new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds)
        );
        Map<Long, Map<String, String>> memberBrandMatrix = new HashMap<>();
        for (UmsMemberBrandLevel bl : allBrandLevels) {
            memberBrandMatrix.computeIfAbsent(bl.getMemberId(), k -> new HashMap<>()).put(bl.getBrand(), bl.getLevelCode());
        }

        // 6. 开始拼装 Excel 行数据
        List<List<Object>> dataList = new ArrayList<>();
        for (UmsMember m : allMembers) {
            List<Object> row = new ArrayList<>();
            // 基础信息
            row.add(m.getName());
            row.add(m.getPhone());
            row.add(m.getBalance() != null ? m.getBalance().toString() : "0.00");
            row.add(m.getCoupon() != null ? m.getCoupon().toString() : "0.00");

            // 满减券张数
            Long vCount = memberVoucherCountMap.getOrDefault(m.getId(), 0L);
            row.add(vCount > 0 ? vCount.toString() : "0");

            // 🌟 动态填充该会员在各个品牌的 VIP 等级
            Map<String, String> myBrandLevels = memberBrandMatrix.getOrDefault(m.getId(), new HashMap<>());
            if (brands != null) {
                for (GmsBrand brand : brands) {
                    String levelCode = myBrandLevels.get(String.valueOf(brand.getId()));
                    if (levelCode != null && levelCodeToNameMap.containsKey(levelCode)) {
                        // 将等级代码(如: V1)翻译成中文(如: 金冠)填入表格
                        row.add(levelCodeToNameMap.get(levelCode));
                    } else {
                        row.add(""); // 没有特权留空
                    }
                }
            }
            dataList.add(row);
        }

        // 7. 响应下载
        writeExcelResponse(response, heads, dataList);
    }

    private void writeExcelResponse(HttpServletResponse response, List<List<String>> heads, List<List<Object>> dataList) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("门店全量会员资产大表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(heads).sheet("老会员数据").doWrite(dataList);
    }
}
