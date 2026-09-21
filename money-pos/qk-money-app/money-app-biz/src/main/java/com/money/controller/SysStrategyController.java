package com.money.controller;

import com.money.feature.sys.infrastructure.persistence.entity.SysStrategy;
import com.money.service.SysStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SysStrategy", description = "策略中心控制台")
@RestController
@RequestMapping("/sys/strategy")
@RequiredArgsConstructor
public class SysStrategyController {

    private final SysStrategyService sysStrategyService;

    @Operation(summary = "获取全局策略")
    @GetMapping("/get")
    public SysStrategy getStrategy() {
        return sysStrategyService.getGlobalStrategy();
    }

    @Operation(summary = "保存/更新全局策略")
    @PostMapping("/save")
    public String saveStrategy(@RequestBody SysStrategy strategy) {
        sysStrategyService.saveGlobalStrategy(strategy);
        return "success";
    }
}
