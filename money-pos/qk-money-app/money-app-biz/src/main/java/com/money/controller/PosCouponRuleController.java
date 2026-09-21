package com.money.controller;

import com.money.contract.member.CouponRuleManagementCommand;
import com.money.contract.member.CouponRuleManagementSnapshot;
import com.money.contract.member.MemberCouponRuleSnapshot;
import com.money.feature.trade.application.coupon.CouponRuleManagementService;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "posCouponRule", description = "满减券规则配置")
@RestController
@RequestMapping("/pos/couponRule")
@RequiredArgsConstructor
public class PosCouponRuleController {

    private final CouponRuleManagementService couponRuleManagementService;

    @GetMapping
    public PageVO<CouponRuleManagementSnapshot> list(@RequestParam(defaultValue = "1") Integer current,
                                      @RequestParam(defaultValue = "10") Integer size,
                                      String name) {
        return couponRuleManagementService.list(current, size, name);
    }

    @PostMapping
    public void add(@RequestBody CouponRuleManagementCommand command) {
        couponRuleManagementService.add(command);
    }

    @PutMapping
    public void update(@RequestBody CouponRuleManagementCommand command) {
        couponRuleManagementService.update(command);
    }

    @DeleteMapping
    public void delete(@RequestBody List<Long> ids) {
        couponRuleManagementService.delete(ids);
    }

    // ==================== 【全新核心：供收银台查询顾客可用卡包】 ====================
    @GetMapping("/memberCoupons/{memberId}")
    public List<MemberCouponRuleSnapshot> getMemberCoupons(@PathVariable("memberId") Long memberId) {
        return couponRuleManagementService.getMemberCoupons(memberId);
    }
}
