package com.money.feature.gms.interfaces.rest;

import com.money.web.dto.ValidGroup;
import com.money.web.vo.PageVO;
import com.money.dto.GmsGoods.GmsGoodsDTO;
import com.money.dto.GmsGoods.GmsGoodsQueryDTO;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.feature.gms.application.product.GmsGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 商品表 前端控制器 (纯净版)
 * 职责：仅处理商品基础资料的增删改查。
 * (POS收银逻辑已剥离至 PosGoodsController，Excel导入逻辑已剥离至 GmsGoodsExcelController)
 */
@Tag(name = "gmsGoods", description = "商品管理基础中心")
@RestController
@RequestMapping("/gms/goods")
@RequiredArgsConstructor
public class GmsGoodsController {

    // 🌟 极度舒适：再也没有那些乱七八糟的 Mapper 注入了！
    private final GmsGoodsService gmsGoodsService;

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:list')")
    public PageVO<GmsGoodsVO> list(@Validated GmsGoodsQueryDTO queryDTO) {
        return gmsGoodsService.list(queryDTO);
    }

    @Operation(summary = "添加")
    @PostMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:add')")
    public void add(@Validated(ValidGroup.Save.class) @RequestPart("goods") GmsGoodsDTO addDTO,
                    @RequestPart(required = false) MultipartFile pic) {
        gmsGoodsService.add(addDTO, pic);
    }

    @Operation(summary = "修改")
    @PutMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:edit')")
    public void update(@Validated(ValidGroup.Update.class) @RequestPart("goods") GmsGoodsDTO updateDTO,
                       @RequestPart(required = false) MultipartFile pic) {
        gmsGoodsService.update(updateDTO, pic);
    }

    @Operation(summary = "删除")
    @DeleteMapping
    @PreAuthorize("@rbac.hasPermission('gmsGoods:del')")
    public void delete(@RequestBody Set<Long> ids) {
        gmsGoodsService.delete(ids);
    }
}
