package com.money.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.money.service.SysBackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "sysBackup", description = "系统级数据灾备中心")
@RestController
@RequestMapping("/sys/backup")
@RequiredArgsConstructor
public class SysBackupController {

    private final SysBackupService sysBackupService;

    // ==========================================
    // 🌟 核心新增：实时日志大屏 SSE 通道
    // ==========================================
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "开启实时日志大屏通道")
    public SseEmitter streamLogs() {
        return sysBackupService.createSseEmitter();
    }

    @GetMapping("/export")
    @PreAuthorize("@rbac.hasRole('SUPER_ADMIN')")
    @Operation(summary = "触发一键热备并流式下载归档 Zip")
    public void exportBackup(HttpServletResponse response) {
        File zipFile = null;
        try {
            zipFile = sysBackupService.createBackupZip("");

            response.setContentType("application/octet-stream");
            String encodedFileName = URLEncoder.encode(zipFile.getName(), "UTF-8").replaceAll("\\+", "%20");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
            response.setContentLengthLong(zipFile.length());

            try (InputStream in = new FileInputStream(zipFile);
                 OutputStream out = response.getOutputStream()) {
                IoUtil.copy(in, out);
                response.flushBuffer();
            }
        } catch (Exception e) {
            log.error("备份下载失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (zipFile != null && zipFile.exists()) {
                FileUtil.del(zipFile);
            }
        }
    }

    @PostMapping("/restore")
    @PreAuthorize("@rbac.hasRole('SUPER_ADMIN')")
    @Operation(summary = "上传 Zip 压缩包执行系统灾难还原")
    public Map<String, Object> restoreBackup(@RequestPart("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            result.put("code", 400);
            result.put("msg", "非法的文件格式，请上传正确的 .zip 归档包！");
            return result;
        }

        try {
            sysBackupService.restoreFromZip(file);

            result.put("code", 205);
            result.put("msg", "数据还原成功！底层内核已重置，请手动关闭本软件并重新启动生效。");
            return result;

        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            return result;
        }
    }
}
