package com.money.feature.ums.application.memberbenefit;

import com.money.contract.member.MemberBrandBenefitLedgerCommand;
import com.money.contract.member.MemberBrandBenefitLedgerCommandHandler;
import com.money.dto.memberbenefit.MemberTargetPlanCreateDTO;
import com.money.dto.memberbenefit.MemberTargetPlanCancelDTO;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.mapper.UmsMemberMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** UMS application boundary for POS plan creation; the ledger remains the only TARGET writer. */
@Service
@RequiredArgsConstructor
public class MemberTargetPlanApplicationService {
    private final UmsMemberMapper memberMapper;
    private final MemberBrandBenefitLedgerCommandHandler ledger;

    public Long create(MemberTargetPlanCreateDTO dto) {
        UmsMember member = memberMapper.selectById(dto.getMemberId());
        if (member == null || Boolean.TRUE.equals(member.getDeleted())) throw new BaseException("会员不存在或已被删除");
        MemberBrandBenefitLedgerCommand.TargetPlanCreate command = new MemberBrandBenefitLedgerCommand.TargetPlanCreate();
        command.setMemberId(dto.getMemberId()); command.setBrandId(dto.getBrandId().trim());
        command.setTargetTierCode(dto.getTargetTierCode().trim());
        command.setInitialProgress(dto.getInitialProgress() == null ? BigDecimal.ZERO : dto.getInitialProgress());
        command.setRequestNo(dto.getReqId().trim()); command.setReason(dto.getReason());
        command.setSourceType("POS_TARGET_PLAN");
        return ledger.createTargetPlan(command);
    }
    public void cancel(MemberTargetPlanCancelDTO dto) {
        ledger.cancelTargetPlan(dto.getPlanId(), dto.getReqId().trim(), null, dto.getReason().trim());
    }
}
