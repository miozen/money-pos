package com.money.controller;

import com.money.feature.trade.application.pos.PosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "umsMemberPos", description = "会员POS收银专线")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberPosController {

    // 🌟 核心：它只依赖跟 POS 收银域相关的服务
    private final PosService posService;

    @GetMapping("/pos-search")
    @Operation(summary = "POS搜会员")
    public java.util.List<com.money.dto.pos.PosMemberVO> posSearchMember(@RequestParam String keyword) {
        return posService.listMember(keyword);
    }

    @GetMapping("/coupon-rules")
    @Operation(summary = "获取满减券规则")
    public java.util.List<com.money.entity.PosCouponRule> getCouponRules() {
        return posService.getValidCouponRules();
    }
}
