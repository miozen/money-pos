package com.money.feature.ums.application.member;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberCouponCountQuery;
import com.money.contract.goods.BrandSelectionQuery;
import com.money.contract.goods.BrandSelectionSnapshot;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** UMS 全量会员资产 Excel 的只读输出服务。 */
@Service
@RequiredArgsConstructor
public class UmsMemberAssetExcelExportService {

    private final UmsMemberExcelTemplateService templateService;
    private final BrandSelectionQuery brandSelectionQuery;
    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final MemberCouponCountQuery memberCouponCountQuery;

    public void writeExport(HttpServletResponse response) throws IOException {
        List<List<String>> heads = templateService.buildDynamicHeads();
        List<BrandSelectionSnapshot> brands = brandSelectionQuery.listBrandSelections();
        List<UmsMember> allMembers = umsMemberMapper.selectList(new LambdaQueryWrapper<UmsMember>());
        if (allMembers == null || allMembers.isEmpty()) {
            writeExcelResponse(response, heads, new ArrayList<>());
            return;
        }

        List<Long> memberIds = allMembers.stream().map(UmsMember::getId).collect(Collectors.toList());
        Map<Long, Long> memberVoucherCountMap = memberCouponCountQuery.countUnusedCouponsByMemberIds(memberIds);
        Map<Long, Map<String, String>> memberBrandMatrix = loadMemberBrandMatrix(memberIds);
        Map<String, String> levelCodeToNameMap = templateService.getMemberTypeNameByCode();

        List<List<Object>> dataList = new ArrayList<>();
        for (UmsMember member : allMembers) {
            List<Object> row = new ArrayList<>();
            row.add(member.getName());
            row.add(member.getPhone());
            row.add(member.getBalance() != null ? member.getBalance().toString() : "0.00");
            row.add(member.getCoupon() != null ? member.getCoupon().toString() : "0.00");
            row.add(String.valueOf(memberVoucherCountMap.getOrDefault(member.getId(), 0L)));

            Map<String, String> myBrandLevels = memberBrandMatrix.getOrDefault(member.getId(), Map.of());
            for (BrandSelectionSnapshot brand : brands) {
                String levelCode = myBrandLevels.get(String.valueOf(brand.getId()));
                row.add(levelCode == null ? "" : levelCodeToNameMap.getOrDefault(levelCode, ""));
            }
            dataList.add(row);
        }
        writeExcelResponse(response, heads, dataList);
    }

    private Map<Long, Map<String, String>> loadMemberBrandMatrix(List<Long> memberIds) {
        Map<Long, Map<String, String>> matrix = new HashMap<>();
        for (UmsMemberBrandLevel level : umsMemberBrandLevelMapper.selectList(
                new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds))) {
            matrix.computeIfAbsent(level.getMemberId(), ignored -> new HashMap<>())
                    .put(level.getBrand(), level.getLevelCode());
        }
        return matrix;
    }

    private void writeExcelResponse(HttpServletResponse response, List<List<String>> heads, List<List<Object>> dataList) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("门店全量会员资产大表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(heads).sheet("老会员数据").doWrite(dataList);
    }
}
