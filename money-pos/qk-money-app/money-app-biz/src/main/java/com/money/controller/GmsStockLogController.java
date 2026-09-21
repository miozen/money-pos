package com.money.controller;

import com.money.dto.GmsGoods.GmsStockLogQueryDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.feature.gms.application.inventory.GmsStockLogQueryService;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "gmsStockLog", description = "进销存台账")
@RestController
@RequestMapping("/gms/stockLog")
@RequiredArgsConstructor
public class GmsStockLogController {

    private final GmsStockLogQueryService gmsStockLogQueryService;

    @Operation(summary = "分页查询库存流水")
    @GetMapping
    public PageVO<GmsStockLog> list(GmsStockLogQueryDTO queryDTO, @RequestParam(required = false) String goodsBarcode) {
        return gmsStockLogQueryService.list(queryDTO, goodsBarcode);
    }
}
