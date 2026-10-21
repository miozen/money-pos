package com.money.feature.ums.interfaces.rest;

import com.money.feature.ums.application.cloudsync.MemberCloudSyncStatus;
import com.money.feature.ums.application.cloudsync.MemberCloudSyncStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "umsMemberCloudSync", description = "Omnia Cloud会员同步只读状态")
@RestController
@RequestMapping("/ums/member-cloud-sync")
@RequiredArgsConstructor
public class UmsMemberCloudSyncController {
    private final MemberCloudSyncStatusService statusService;

    @Operation(summary = "查询Omnia Cloud本地同步状态")
    @GetMapping("/status")
    @PreAuthorize("@rbac.hasPermission('omniaCloud:view')")
    public MemberCloudSyncStatus status() {
        return statusService.status();
    }
}
