package com.money.feature.ums.interfaces.rest;

import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.dto.UmsMember.MemberRankVO;
import com.money.feature.ums.application.member.UmsMemberService;
import com.money.feature.ums.application.member.UmsMemberServiceImpl.MemberGoodsRankVO;
import com.money.web.dto.ValidGroup;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j
@Tag(name = "umsMember", description = "会员基础管理中心")
@RestController
@RequestMapping("/ums/member")
@RequiredArgsConstructor
public class UmsMemberController {

    private final UmsMemberService umsMemberService;

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public PageVO<UmsMemberVO> list(@Validated UmsMemberQueryDTO queryDTO) {
        return umsMemberService.list(queryDTO);
    }

    @Operation(summary = "获取会员画像商品排行")
    @GetMapping("/top20Goods")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<MemberGoodsRankVO> top20Goods(@RequestParam Long memberId) {
        return umsMemberService.getTop20Goods(memberId);
    }

    // 🌟 新增：标准的单条会员详情查询接口
    @Operation(summary = "获取单个会员详情")
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public UmsMemberVO getInfo(@PathVariable("id") Long id) {
        return umsMemberService.getDetail(id);
    }

    @Operation(summary = "添加会员")
    @PostMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:add')")
    public void add(@Validated(ValidGroup.Save.class) @RequestBody UmsMemberDTO addDTO) {
        umsMemberService.add(addDTO);
    }

    @Operation(summary = "修改会员")
    @PutMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:edit')")
    public void update(@Validated(ValidGroup.Update.class) @RequestBody UmsMemberDTO updateDTO) {
        umsMemberService.update(updateDTO);
    }

    @Operation(summary = "删除会员")
    @DeleteMapping
    @PreAuthorize("@rbac.hasPermission('umsMember:del')")
    public void delete(@RequestBody Set<Long> ids) {
        umsMemberService.delete(ids);
    }

    @GetMapping("/dormant")
    @Operation(summary = "查询沉睡会员")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    public List<UmsMemberVO> dormantList(@RequestParam Integer days) {
        return umsMemberService.getDormantMembers(days);
    }

    @Operation(summary = "排行榜-累计消费Top50")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    @GetMapping("/rank/consume")
    public List<MemberRankVO> getTopConsume() {
        return umsMemberService.getTopConsumeMembers();
    }

    @Operation(summary = "排行榜-余额Top50")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    @GetMapping("/rank/balance")
    public List<MemberRankVO> getTopBalance() {
        return umsMemberService.getTopBalanceMembers();
    }

    @Operation(summary = "排行榜-频次Top50")
    @PreAuthorize("@rbac.hasPermission('umsMember:list')")
    @GetMapping("/rank/frequency")
    public List<MemberRankVO> getTopFrequency() {
        return umsMemberService.getTopFrequencyMembers();
    }
}
