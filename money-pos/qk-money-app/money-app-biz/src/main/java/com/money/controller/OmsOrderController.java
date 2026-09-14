package com.money.controller;

import cn.hutool.core.util.StrUtil;
import com.money.constant.BizErrorStatus;
// 🌟 必须显式导入，拒绝通配符
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.dto.OmsOrder.ReturnOrderDTO;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.feature.trade.application.orderquery.OmsOrderService;
import com.money.feature.trade.application.refund.OmsOrderRefundService;
import com.money.feature.fin.application.analysis.OmsSalesAnalysisService;
import com.money.service.printer.PosPrinterService;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "oms-order", description = "订单与营销管理 (V8.1 防并发锁死版)")
@RestController
@RequestMapping("/oms-order")
@RequiredArgsConstructor
public class OmsOrderController {

    private final OmsOrderService omsOrderService;
    private final OmsOrderRefundService omsOrderRefundService;
    private final OmsSalesAnalysisService omsSalesAnalysisService;
    private final PosPrinterService posPrinterService;

    @Operation(summary = "订单分页列表")
    @GetMapping("/page")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO) {
        return omsOrderService.list(queryDTO);
    }

    @Operation(summary = "订单统计看板")
    @GetMapping("/statistics")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public OrderCountVO statistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return omsSalesAnalysisService.countOrderAndSales(startTime, endTime);
    }

    @Operation(summary = "订单详情 (支持ID或单号)")
    @GetMapping("/detail")
    @PreAuthorize("@rbac.hasPermission('oms:order:list')")
    public OrderDetailVO getOrderDetail(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String orderNo) {
        if (id == null && StrUtil.isBlank(orderNo)) {
            throw new BaseException(BizErrorStatus.POS_SETTLE_REQ_EMPTY, "必须提供订单ID或订单编号");
        }
        return StrUtil.isNotBlank(orderNo) ? omsOrderService.getOrderDetailByNo(orderNo) : omsOrderService.getOrderDetail(id);
    }

    @Operation(summary = "整单退款 (接入领域防线与防并发)")
    @PostMapping("/return")
    @PreAuthorize("@rbac.hasPermission('oms:order:return')")
    public void returnOrder(@Validated @RequestBody ReturnOrderDTO dto) { // 🌟 采用外部独立DTO
        omsOrderRefundService.returnOrder(dto.getReqId(), dto.getOrderNo());
    }

    @Operation(summary = "部分商品退货 (接入领域防线与防并发)")
    @PostMapping("/returnGoods")
    @PreAuthorize("@rbac.hasPermission('oms:order:return')")
    public void returnGoods(@Validated @RequestBody ReturnGoodsDTO dto) { // 🌟 采用外部独立DTO
        omsOrderRefundService.returnGoods(dto);
    }

    @Operation(summary = "利润审计分页")
    @GetMapping("/profit-audit")
    @PreAuthorize("@rbac.hasPermission('oms:order:audit')")
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        return omsSalesAnalysisService.getProfitAuditPage(queryDTO);
    }

    @Operation(summary = "硬件级：打印小票并弹开钱箱")
    @GetMapping("/hardware/print")
    public Boolean printHardwareReceipt(@RequestParam String orderNo) {
        OrderDetailVO orderVO = omsOrderService.getOrderDetailByNo(orderNo);
        posPrinterService.printReceiptAndOpenDrawer(orderVO);
        return true;
    }
}