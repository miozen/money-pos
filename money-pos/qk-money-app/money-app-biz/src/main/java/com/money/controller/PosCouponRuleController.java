package com.money.controller;

import com.money.entity.PosCouponRule;
import com.money.feature.trade.application.coupon.CouponRuleManagementService;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "posCouponRule", description = "满减券规则配置")
@RestController
@RequestMapping("/pos/couponRule")
@RequiredArgsConstructor
public class PosCouponRuleController {

    private final CouponRuleManagementService couponRuleManagementService;

    @GetMapping
    public PageVO<PosCouponRule> list(@RequestParam(defaultValue = "1") Integer current,
                                      @RequestParam(defaultValue = "10") Integer size,
                                      String name) {
        return couponRuleManagementService.list(current, size, name);
    }

    @PostMapping
    public void add(@RequestBody PosCouponRule rule) {
        couponRuleManagementService.add(rule);
    }

    @PutMapping
    public void update(@RequestBody PosCouponRule rule) {
        couponRuleManagementService.update(rule);
    }

    @DeleteMapping
    public void delete(@RequestBody List<Long> ids) {
        couponRuleManagementService.delete(ids);
    }

    // ==================== 【全新核心：供收银台查询顾客可用卡包】 ====================
    @GetMapping("/memberCoupons/{memberId}")
    public List<Map<String, Object>> getMemberCoupons(@PathVariable("memberId") Long memberId) {
        return couponRuleManagementService.getMemberCoupons(memberId);
    }
}
