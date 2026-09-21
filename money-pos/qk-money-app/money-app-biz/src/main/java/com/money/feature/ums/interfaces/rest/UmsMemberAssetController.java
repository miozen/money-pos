package com.money.feature.ums.interfaces.rest;

import cn.hutool.core.util.StrUtil;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberLog;
import com.money.feature.ums.infrastructure.persistence.entity.UmsRechargeOrder;
import com.money.feature.ums.application.memberasset.UmsMemberAssetService; // 🌟 引入标准的资产服务
import com.money.feature.ums.application.member.UmsMemberService;
import com.money.web.exception.BaseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "umsMemberAsset", description = "会员资产与充值中心")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberAssetController {

    private final UmsMemberService umsMemberService;
    private final UmsMemberAssetService umsMemberAssetService; // 🌟 架构规范化：由 Service 全面接管数据层

    @GetMapping("/logs")
    @Operation(summary = "查询会员资产流水")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<UmsMemberLog> getMemberLogs(@RequestParam Long memberId) {
        // 🌟 路由转发：将请求委托给 Service，方便后期织入审计和脱敏逻辑
        return umsMemberAssetService.getMemberLogs(memberId);
    }

    @PostMapping("/recharge")
    @Operation(summary = "会员充值业务")
    @PreAuthorize("@rbac.hasPermission('umsMember:recharge')")
    public void recharge(@RequestBody com.money.dto.Ums.RechargeDTO dto) {
        umsMemberService.recharge(dto);
    }

    @Operation(summary = "根据单号获取充值订单详情")
    @GetMapping("/recharge/order/{orderNo}")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public UmsRechargeOrder getRechargeOrderDetail(@PathVariable String orderNo) {
        if (StrUtil.isBlank(orderNo)) throw new BaseException("单号不能为空");

        // 🌟 路由转发：业务异常判断等逻辑下沉至 Service
        return umsMemberAssetService.getRechargeOrderDetail(orderNo);
    }

    @Operation(summary = "执行红冲撤销")
    @PostMapping("/recharge/void")
    @PreAuthorize("@rbac.hasPermission('umsMember:void')")
    public void voidRechargeOrder(@RequestBody Map<String, String> params) {
        String orderNo = params.get("orderNo");
        String reason = params.get("reason");
        if (StrUtil.isBlank(orderNo)) throw new BaseException("单号不能为空");
        if (StrUtil.isBlank(reason)) throw new BaseException("红冲原因不能为空");

        umsMemberService.voidRecharge(orderNo, reason);
    }
}
