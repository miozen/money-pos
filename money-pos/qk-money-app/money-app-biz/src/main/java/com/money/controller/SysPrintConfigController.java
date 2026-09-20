package com.money.controller;

import com.money.feature.sys.infrastructure.persistence.entity.SysPrintConfig;
import com.money.service.SysPrintConfigService;
import com.money.web.exception.BaseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 小票打印机与钱箱硬件配置控制器
 */
@Tag(name = "System", description = "系统设置与硬件配置")
@RestController
@RequestMapping("/system/config/print")
@RequiredArgsConstructor
public class SysPrintConfigController {

    private final SysPrintConfigService sysPrintConfigService;

    @Operation(summary = "获取当前小票打印与钱箱设置 (ID:1)")
    @GetMapping
    public SysPrintConfig getConfig() {
        return sysPrintConfigService.getConfig();
    }

    @Operation(summary = "更新小票打印与钱箱设置 (强审计)")
    @PostMapping("/update")
    public Boolean updateConfig(@RequestBody SysPrintConfig config) {
        boolean success = sysPrintConfigService.updateConfig(config);
        if (!success) {
            throw new BaseException("硬件打印配置更新失败，请重试！");
        }
        return true;
    }
}
