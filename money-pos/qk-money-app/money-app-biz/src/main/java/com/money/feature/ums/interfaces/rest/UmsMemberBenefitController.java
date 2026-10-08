package com.money.feature.ums.interfaces.rest;

import com.money.dto.memberbenefit.MemberBenefitOverviewVO;
import com.money.dto.memberbenefit.MemberBenefitTierDTO;
import com.money.dto.memberbenefit.MemberTargetProgressLogVO;
import com.money.feature.ums.application.memberbenefit.MemberBenefitReadService;
import com.money.feature.ums.application.memberbenefit.MemberBenefitTierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "umsMemberBenefit", description = "会员品牌权益查询")
@RestController
@RequestMapping("/ums/member-benefit")
@RequiredArgsConstructor
public class UmsMemberBenefitController {
    private final MemberBenefitReadService memberBenefitReadService;
    private final MemberBenefitTierService memberBenefitTierService;

    @GetMapping("/tiers")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:tier')")
    public List<MemberBenefitTierDTO> tiers() {
        return memberBenefitTierService.list();
    }

    @PostMapping("/tiers")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:tier')")
    public void saveTier(@Validated(MemberBenefitTierDTO.Create.class) @RequestBody MemberBenefitTierDTO dto) {
        memberBenefitTierService.save(dto);
    }

    @DeleteMapping("/tiers/{id}")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:tier')")
    public void deleteTier(@PathVariable Long id) {
        memberBenefitTierService.delete(id);
    }

    @Operation(summary = "查询会员权益、品牌档位与TARGET计划")
    @GetMapping("/overview")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate', 'memberBenefit:manage')")
    public MemberBenefitOverviewVO overview(@RequestParam(required = false) Long memberId,
                                            @RequestParam(required = false) String brandId) {
        return memberBenefitReadService.overview(memberId, brandId);
    }

    @Operation(summary = "查询TARGET进度审计流水")
    @GetMapping("/target-logs")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate', 'memberBenefit:manage')")
    public List<MemberTargetProgressLogVO> targetLogs(@RequestParam Long planId) {
        return memberBenefitReadService.targetLogs(planId);
    }
}
