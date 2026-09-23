package com.money.controller;

import com.money.dto.LoginDTO;
import com.money.security.annotation.CurrentUser;
import com.money.service.SysAuthService;
import com.money.vo.AuthTokenVO;
import com.money.vo.UserInfoVO;
import com.money.vo.VueRouterVO;
import com.money.vo.LoginCandidateVO;
import com.money.web.exception.BaseException;
import com.money.web.response.RStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.List;
@Tag(name = "auth", description = "认证访问")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class SysAuthController {

    private final SysAuthService sysAuthService;

    @Value("${money.tenant.header:Y-tenant}")
    private String tenantHeader;

    @Operation(summary = "获取VUE菜单")
    @GetMapping("/router")
    public List<VueRouterVO> getVueRouter(@CurrentUser Long userId) {
        return sysAuthService.getVueRouter(userId);
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public AuthTokenVO login(@Validated @RequestBody LoginDTO loginDto) {
        return sysAuthService.login(loginDto);
    }

    @Operation(summary = "本机后台登录账号候选")
    @GetMapping("/login-candidates")
    public List<LoginCandidateVO> loginCandidates(HttpServletRequest request, HttpServletResponse response) {
        requireLocalDefaultTenantRequest(request);
        response.setHeader("Cache-Control", "no-store");
        return sysAuthService.getLoginCandidates();
    }

    @Operation(summary = "注销")
    @PostMapping("/logout")
    public void logout(@Parameter(hidden = true) @RequestHeader("${money.security.token.header}") String token) {
        sysAuthService.logout(token);
    }

    @Operation(summary = "获取个人信息")
    @GetMapping("/own")
    public UserInfoVO own(@Parameter(hidden = true) @CurrentUser String username) {
        return sysAuthService.getUserInfo(username);
    }

    @Operation(summary = "刷新令牌")
    @GetMapping("/refreshToken")
    public AuthTokenVO refreshToken(@Parameter(hidden = true) @CurrentUser String username, String refreshToken) {
        return sysAuthService.refreshToken(username, refreshToken);
    }

    private void requireLocalDefaultTenantRequest(HttpServletRequest request) {
        if (request.getHeader(tenantHeader) != null || !isLoopback(request.getRemoteAddr())) {
            throw new BaseException(RStatus.FORBIDDEN);
        }
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

}
