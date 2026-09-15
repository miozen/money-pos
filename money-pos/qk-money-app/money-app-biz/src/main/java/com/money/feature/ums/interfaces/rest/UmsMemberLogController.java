package com.money.feature.ums.interfaces.rest;

import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberLogQueryDTO;
import com.money.dto.UmsMember.UmsMemberLogVO;
import com.money.feature.ums.application.memberlog.UmsMemberLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "umsMemberLog", description = "会员资金流水")
@RestController
@RequestMapping("/ums/member-log")
@RequiredArgsConstructor
public class UmsMemberLogController {

    private final UmsMemberLogService umsMemberLogService;

    @Operation(summary = "分页查询流水")
    @GetMapping
    public PageVO<UmsMemberLogVO> list(UmsMemberLogQueryDTO queryDTO) {
        return umsMemberLogService.list(queryDTO);
    }
}
