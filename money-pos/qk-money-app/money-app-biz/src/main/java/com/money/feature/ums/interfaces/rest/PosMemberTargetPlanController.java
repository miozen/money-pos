package com.money.feature.ums.interfaces.rest;

import com.money.dto.memberbenefit.MemberTargetPlanCreateDTO;
import com.money.feature.ums.application.memberbenefit.MemberTargetPlanApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POS route deliberately owned by the UMS TARGET ledger application boundary. */
@RestController
@RequestMapping("/pos/target")
@RequiredArgsConstructor
public class PosMemberTargetPlanController {
    private final MemberTargetPlanApplicationService targetPlanApplicationService;

    @PostMapping("/plans")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public Long create(@Validated @RequestBody MemberTargetPlanCreateDTO dto) {
        return targetPlanApplicationService.create(dto);
    }
}
