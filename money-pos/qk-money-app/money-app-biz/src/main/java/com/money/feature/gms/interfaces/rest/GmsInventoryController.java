package com.money.feature.gms.interfaces.rest;

import com.money.dto.GmsGoods.InventoryDocRequestDTO;
import com.money.feature.gms.application.inventory.GmsInventoryDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "gmsInventory", description = "进销存大一统单据路由")
@RestController
@RequestMapping("/gms/inventory")
@RequiredArgsConstructor
public class GmsInventoryController {

    private final GmsInventoryDocService inventoryDocService;

    // 🌟 完全兼容前端现有的 3 个 Api 接口路径

    @Operation(summary = "提交采购入库单")
    @PostMapping("/inbound")
    @PreAuthorize("@rbac.hasPermission('gmsGoods:edit')")
    public void createInbound(@RequestBody InventoryDocRequestDTO requestDTO) {
        requestDTO.setType("INBOUND"); // 强行指定类型，防止前端漏传
        inventoryDocService.executeDoc(requestDTO);
    }

    @Operation(summary = "提交库存盘点单")
    @PostMapping("/check")
    @PreAuthorize("@rbac.hasPermission('gmsGoods:edit')")
    public void createCheck(@RequestBody InventoryDocRequestDTO requestDTO) {
        requestDTO.setType("CHECK");
        inventoryDocService.executeDoc(requestDTO);
    }

    @Operation(summary = "提交报损出库单")
    @PostMapping("/outbound")
    @PreAuthorize("@rbac.hasPermission('gmsGoods:edit')")
    public void createOutbound(@RequestBody InventoryDocRequestDTO requestDTO) {
        requestDTO.setType("OUTBOUND");
        inventoryDocService.executeDoc(requestDTO);
    }
}
