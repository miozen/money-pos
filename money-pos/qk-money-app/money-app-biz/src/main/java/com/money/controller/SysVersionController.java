package com.money.controller;

import com.money.service.ReleaseVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "System", description = "系统运行信息")
@RestController
@RequestMapping("/system/version")
@RequiredArgsConstructor
public class SysVersionController {
    private final ReleaseVersionService releaseVersionService;

    @Operation(summary = "读取当前后端构建版本")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> currentVersion() {
        return Map.of("version", releaseVersionService.currentVersion());
    }
}
