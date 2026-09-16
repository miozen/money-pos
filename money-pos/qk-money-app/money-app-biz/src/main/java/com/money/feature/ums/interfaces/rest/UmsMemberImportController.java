package com.money.feature.ums.interfaces.rest;

import com.money.feature.ums.application.member.UmsMemberAssetExcelExportService;
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

@Slf4j
@Tag(name = "umsMemberImport", description = "会员运营导入中心")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberImportController {

    private final UmsMemberService umsMemberService;
    private final UmsMemberExcelTemplateService umsMemberExcelTemplateService;
    private final UmsMemberAssetExcelExportService umsMemberAssetExcelExportService;

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
        umsMemberAssetExcelExportService.writeExport(response);
    }
}
