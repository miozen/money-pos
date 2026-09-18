package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.dto.Ums.RechargeDTO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.dto.UmsMember.MemberRankVO;
import com.money.dto.UmsMember.MemberGoodsRankVO;
import com.money.entity.UmsMember;
import com.money.feature.ums.application.memberasset.UmsMemberAssetService;
import com.money.feature.ums.application.memberasset.UmsMemberRechargeService;
import com.money.mapper.UmsMemberMapper;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 会员领域 核心门面枢纽 (Facade / Application Orchestrator)
 * 🌟 极致解耦：只保留向四大专职子域转发指令的能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberServiceImpl extends ServiceImpl<UmsMemberMapper, UmsMember> implements UmsMemberService {

    // 🌟 注入刚拆分完毕的 4 大核心子域
    private final UmsMemberProfileService UmsMemberProfileService;
    private final UmsMemberAssetService UmsMemberAssetService;
    private final UmsMemberRechargeService UmsMemberRechargeService;
    private final UmsMemberImportService UmsMemberImportService;

    // ==========================================
    // 1. 会员档案与查询域
    // ==========================================
    @Override
    public PageVO<UmsMemberVO> list(UmsMemberQueryDTO queryDTO) {
        return UmsMemberProfileService.list(queryDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(UmsMemberDTO addDTO) {
        UmsMemberProfileService.add(addDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UmsMemberDTO updateDTO) {
        UmsMemberProfileService.update(updateDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        UmsMemberProfileService.delete(ids);
    }

    @Override
    public List<MemberGoodsRankVO> getTop20Goods(Long memberId) {
        return UmsMemberProfileService.getTop20Goods(memberId);
    }

    @Override
    public List<UmsMemberVO> getDormantMembers(Integer days) {
        return UmsMemberProfileService.getDormantMembers(days);
    }

    // ==========================================
    // 2. 会员资产域 (消费、扣款、退货)
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long id, BigDecimal amount, BigDecimal couponAmount, String orderNo) {
        UmsMemberAssetService.consume(id, amount, couponAmount, orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(Long memberId, BigDecimal amount, String orderNo, String remark) {
        UmsMemberAssetService.deductBalance(memberId, amount, orderNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo) {
        UmsMemberAssetService.processReturn(id, amount, coupon, increaseCancelTimes, orderNo);
    }

    // ==========================================
    // 3. 会员充值域
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(RechargeDTO dto) {
        UmsMemberRechargeService.recharge(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidRecharge(String orderNo, String reason) {
        UmsMemberRechargeService.voidRecharge(orderNo, reason);
    }

    // ==========================================
    // 4. 会员导入与发券运营域
    // ==========================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchIssueVoucher(List<Long> memberIds, Long ruleId, Integer quantity) {
        UmsMemberImportService.batchIssueVoucher(memberIds, ruleId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importMembers(MultipartFile file) {
        return UmsMemberImportService.importMembers(file); // 🌟 加上 return
    }

    // 🌟 找到引入 UmsMemberProfileService 的地方，加上这段实现
    @Override
    public UmsMemberVO getDetail(Long id) {
        return UmsMemberProfileService.getDetail(id); // 直接把活儿派给档案子域
    }

    @Override
    public List<MemberRankVO> getTopConsumeMembers() {
        return baseMapper.getTopConsumeMembers();
    }

    @Override
    public List<MemberRankVO> getTopBalanceMembers() {
        return baseMapper.getTopBalanceMembers();
    }

    @Override
    public List<MemberRankVO> getTopFrequencyMembers() {
        return baseMapper.getTopFrequencyMembers();
    }
}
