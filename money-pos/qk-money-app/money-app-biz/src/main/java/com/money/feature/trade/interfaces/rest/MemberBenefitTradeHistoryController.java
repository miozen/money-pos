package com.money.feature.trade.interfaces.rest;

import com.money.dto.memberbenefit.MemberBenefitTradeHistoryVO;
import com.money.feature.trade.application.memberpickup.MemberBenefitTradeHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "memberBenefitTradeHistory", description = "会员权益交易历史")
@RestController
@RequestMapping("/member-benefit/trade-history")
@RequiredArgsConstructor
public class MemberBenefitTradeHistoryController {
    private final MemberBenefitTradeHistoryService historyService;

    @Operation(summary = "查询会员权益提货及非商品凭证历史")
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate', 'memberBenefit:manage')")
    public MemberBenefitTradeHistoryVO list(@RequestParam Long memberId) {
        return historyService.listByMember(memberId);
    }
}
