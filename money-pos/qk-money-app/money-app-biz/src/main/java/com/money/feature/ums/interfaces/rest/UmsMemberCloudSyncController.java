package com.money.feature.ums.interfaces.rest;

import com.money.feature.ums.application.cloudsync.MemberCloudSyncStatus;
import com.money.feature.ums.application.cloudsync.MemberCloudSyncStatusService;
import com.money.feature.ums.application.cloudsync.MemberCloudSyncConfigurationDTO;
import com.money.feature.ums.application.cloudsync.MemberCloudSyncConfigurationService;
import com.money.feature.ums.application.cloudsync.MemberCloudSyncSecretRevealDTO;
import com.money.web.exception.BaseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;

@Tag(name = "umsMemberCloudSync", description = "Omnia Cloud会员同步只读状态")
@RestController
@RequestMapping("/ums/member-cloud-sync")
@RequiredArgsConstructor
public class UmsMemberCloudSyncController {
    private final MemberCloudSyncStatusService statusService;
    private final MemberCloudSyncConfigurationService configurationService;

    @Operation(summary = "查询Omnia Cloud本地同步状态")
    @GetMapping("/status")
    @PreAuthorize("@rbac.hasPermission('omniaCloud:view')")
    public MemberCloudSyncStatus status() {
        return statusService.status();
    }

    @Operation(summary = "读取Omnia Cloud本机配置（不返回密钥）")
    @GetMapping("/config")
    @PreAuthorize("@rbac.hasPermission('omniaCloud:view')")
    public MemberCloudSyncConfigurationDTO configuration() { return configurationService.view(); }

    @Operation(summary = "显示已保存的Omnia Cloud本机密钥，仅限本机管理员")
    @PostMapping("/config/reveal-secrets")
    @PreAuthorize("@rbac.hasRole('SUPER_ADMIN') and @rbac.hasPermission('omniaCloud:manage')")
    public MemberCloudSyncSecretRevealDTO revealSecrets(HttpServletRequest httpRequest) {
        requireLoopback(httpRequest);
        return configurationService.revealSecrets();
    }

    @Operation(summary = "保存Omnia Cloud本机配置，仅限本机管理员")
    @PostMapping("/config")
    @PreAuthorize("@rbac.hasRole('SUPER_ADMIN') and @rbac.hasPermission('omniaCloud:manage')")
    public MemberCloudSyncConfigurationDTO saveConfiguration(@RequestBody MemberCloudSyncConfigurationDTO request, HttpServletRequest httpRequest) {
        requireLoopback(httpRequest);
        return configurationService.save(request);
    }

    @Operation(summary = "重新投递当前会员云端快照，仅限本机管理员")
    @PostMapping("/repair")
    @PreAuthorize("@rbac.hasRole('SUPER_ADMIN') and @rbac.hasPermission('omniaCloud:manage')")
    public void beginRepair(HttpServletRequest httpRequest) {
        requireLoopback(httpRequest);
        configurationService.beginRepair();
    }

    @Operation(summary = "立即执行一轮Omnia Cloud待发箱发送，仅限本机管理员")
    @PostMapping("/dispatch")
    @PreAuthorize("@rbac.hasRole('SUPER_ADMIN') and @rbac.hasPermission('omniaCloud:manage')")
    public void dispatchNow(HttpServletRequest httpRequest) {
        requireLoopback(httpRequest);
        configurationService.dispatchNow();
    }

    @Operation(summary = "测试Omnia Cloud签名连接和D1可用性，仅限本机管理员")
    @PostMapping("/test-connection")
    @PreAuthorize("@rbac.hasRole('SUPER_ADMIN') and @rbac.hasPermission('omniaCloud:manage')")
    public void testConnection(HttpServletRequest httpRequest) {
        requireLoopback(httpRequest);
        configurationService.testConnection();
    }

    private void requireLoopback(HttpServletRequest request) {
        try {
            if (request == null || !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) throw new BaseException("Omnia Cloud配置只允许在本机修改");
        } catch (java.net.UnknownHostException ex) {
            throw new BaseException("无法确认配置请求来源");
        }
    }
}
