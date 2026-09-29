package com.money.feature.trade.application.membertarget;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.PayMethodEnum;
import com.money.contract.member.MemberBalancePaymentCommand;
import com.money.contract.member.MemberBalancePaymentCommandHandler;
import com.money.contract.member.MemberBrandBenefitLedgerCommand;
import com.money.contract.member.MemberBrandBenefitLedgerCommandHandler;
import com.money.contract.member.MemberTargetPlanQuery;
import com.money.contract.member.MemberTargetPlanSnapshot;
import com.money.dto.pos.MemberTargetAdjustmentDTO;
import com.money.dto.pos.MemberTargetConfirmDTO;
import com.money.dto.pos.MemberTargetConfirmVO;
import com.money.dto.pos.MemberTargetReceiptVO;
import com.money.dto.pos.MemberTargetSettleDTO;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO;
import com.money.feature.trade.application.checkout.CheckoutOrchestrator;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetReceipt;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetReceiptPay;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetSaleContribution;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetReceiptMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetReceiptPayMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetSaleContributionMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** TARGET keeps ordinary sales in the existing checkout pipeline and records only its additional progress audit. */
@Service @RequiredArgsConstructor
public class MemberTargetBenefitService {
    private final CheckoutOrchestrator checkoutOrchestrator;
    private final MemberTargetPlanQuery targetPlanQuery;
    private final MemberBrandBenefitLedgerCommandHandler ledger;
    private final OmsMemberTargetSaleContributionMapper contributionMapper;
    private final OmsMemberTargetReceiptMapper receiptMapper;
    private final OmsMemberTargetReceiptPayMapper receiptPayMapper;
    private final MemberBalancePaymentCommandHandler balancePayment;
    private final OmsOrderDetailMapper orderDetailMapper;

    @Transactional(rollbackFor = Exception.class)
    public SettleResultVO settle(MemberTargetSettleDTO dto) {
        require(dto != null && dto.getSettle() != null && dto.getTargetPlanId() != null, "TARGET结算请求不完整");
        MemberTargetPlanSnapshot plan = active(dto.getTargetPlanId());
        require(plan.getMemberId().equals(dto.getSettle().getMember()), "TARGET计划与结算会员不一致");
        SettleResultVO result = checkoutOrchestrator.orchestrate(dto.getSettle());
        OmsMemberTargetSaleContribution old = contributionMapper.selectOne(new LambdaQueryWrapper<OmsMemberTargetSaleContribution>()
                .eq(OmsMemberTargetSaleContribution::getTargetPlanId, plan.getPlanId()).eq(OmsMemberTargetSaleContribution::getOrderNo, result.getOrderNo()));
        if (old != null) return result;
        List<com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail> details = orderDetailMapper.selectList(new LambdaQueryWrapper<com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail>()
                .eq(com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail::getOrderNo, result.getOrderNo()));
        require(!details.isEmpty() && details.stream().allMatch(d -> plan.getBrandId().equals(String.valueOf(d.getBrandId()))), "TARGET结算商品必须全部属于计划品牌");
        BigDecimal amount = scale(result.getFinalPayAmount());
        OmsMemberTargetSaleContribution contribution = new OmsMemberTargetSaleContribution();
        contribution.setTargetPlanId(plan.getPlanId()); contribution.setOrderNo(result.getOrderNo()); contribution.setMemberId(plan.getMemberId());
        contribution.setBrandId(plan.getBrandId()); contribution.setContributionAmount(amount); contribution.setStatus("COMPLETED"); contributionMapper.insert(contribution);
        progress(plan, amount, "SALE_CONTRIBUTION", dto.getSettle().getReqId(), "TARGET_SALE", result.getOrderNo(), null);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public MemberTargetReceiptVO supplement(MemberTargetAdjustmentDTO dto) { return adjustment(dto, "SUPPLEMENT", true); }

    @Transactional(rollbackFor = Exception.class)
    public MemberTargetReceiptVO waive(MemberTargetAdjustmentDTO dto) { return adjustment(dto, "WAIVER", false); }

    @Transactional(rollbackFor = Exception.class)
    public MemberTargetConfirmVO confirm(MemberTargetConfirmDTO dto) {
        require(dto != null && dto.getTargetPlanId() != null && text(dto.getReqId()), "TARGET确认请求不完整");
        active(dto.getTargetPlanId()); ledger.confirmTargetPlan(dto.getTargetPlanId(), dto.getReqId(), null, dto.getReason());
        MemberTargetConfirmVO result = new MemberTargetConfirmVO(); result.setTargetPlanId(dto.getTargetPlanId()); result.setStatus("CONFIRMED"); return result;
    }

    /** A sale refund never changes a confirmed tier automatically; it leaves an auditable review marker. */
    public void markRefundReviewRequired(String requestNo, String orderNo) {
        List<OmsMemberTargetSaleContribution> rows = contributionMapper.selectList(new LambdaQueryWrapper<OmsMemberTargetSaleContribution>().eq(OmsMemberTargetSaleContribution::getOrderNo, orderNo));
        for (OmsMemberTargetSaleContribution row : rows) ledger.markTargetPlanReviewRequired(row.getTargetPlanId(), requestNo + "-TARGET-" + row.getTargetPlanId(), orderNo, "TARGET关联销售发生退款，需人工复核");
    }

    private MemberTargetReceiptVO adjustment(MemberTargetAdjustmentDTO dto, String type, boolean paid) {
        require(dto != null && dto.getTargetPlanId() != null && text(dto.getReqId()) && positive(dto.getAmount()), "TARGET调整请求不完整");
        OmsMemberTargetReceipt existing = receiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberTargetReceipt>().eq(OmsMemberTargetReceipt::getRequestNo, dto.getReqId()));
        if (existing != null) return receiptResult(existing);
        MemberTargetPlanSnapshot plan = active(dto.getTargetPlanId());
        if (paid) writePayments(dto, plan); else require(dto.getPayments() == null || dto.getPayments().isEmpty(), "TARGET豁免不得录入支付");
        String receiptNo = "MTR" + IdUtil.getSnowflakeNextIdStr();
        OmsMemberTargetReceipt receipt = new OmsMemberTargetReceipt(); receipt.setReceiptNo(receiptNo); receipt.setRequestNo(dto.getReqId()); receipt.setTargetPlanId(plan.getPlanId()); receipt.setMemberId(plan.getMemberId()); receipt.setReceiptType(type); receipt.setAmount(scale(dto.getAmount())); receipt.setStatus("COMPLETED"); receipt.setReason(dto.getReason() == null ? "" : dto.getReason()); receiptMapper.insert(receipt);
        if (paid) savePayments(receiptNo, dto, plan.getMemberId());
        progress(plan, receipt.getAmount(), type, dto.getReqId(), "TARGET_" + type, receiptNo, dto.getReason());
        return receiptResult(receipt);
    }

    private void writePayments(MemberTargetAdjustmentDTO dto, MemberTargetPlanSnapshot plan) {
        require(dto.getPayments() != null && !dto.getPayments().isEmpty(), "TARGET补差支付明细为空"); BigDecimal total = BigDecimal.ZERO;
        for (SettleAccountsDTO.PaymentItem p : dto.getPayments()) { require(p != null && positive(p.getPayAmount()) && text(p.getPayMethodCode()), "TARGET补差支付明细不合法"); total = total.add(p.getPayAmount()); }
        require(scale(total).compareTo(scale(dto.getAmount())) == 0, "TARGET补差支付必须等于补差金额");
    }
    private void savePayments(String receiptNo, MemberTargetAdjustmentDTO dto, Long memberId) {
        for (SettleAccountsDTO.PaymentItem p : dto.getPayments()) { PayMethodEnum method = PayMethodEnum.fromCode(p.getPayMethodCode().trim().toUpperCase()); require(method != null && (method != PayMethodEnum.AGGREGATE || text(p.getPayTag())), "TARGET补差支付方式不合法"); OmsMemberTargetReceiptPay pay = new OmsMemberTargetReceiptPay(); pay.setReceiptNo(receiptNo); pay.setPayMethodCode(method.getCode()); pay.setPayMethodName(p.getPayMethodName() == null ? method.getCode() : p.getPayMethodName()); pay.setPayTag(p.getPayTag()); pay.setPayAmount(scale(p.getPayAmount())); receiptPayMapper.insert(pay); if (method == PayMethodEnum.BALANCE) { MemberBalancePaymentCommand c = new MemberBalancePaymentCommand(); c.setMemberId(memberId); c.setReceiptNo(receiptNo); c.setRequestNo(dto.getReqId() + "-BALANCE"); c.setAmount(pay.getPayAmount()); c.setRefund(false); balancePayment.handle(c); } }
    }
    private void progress(MemberTargetPlanSnapshot plan, BigDecimal amount, String action, String requestNo, String sourceType, String sourceNo, String reason) { MemberBrandBenefitLedgerCommand.TargetProgressChange c = new MemberBrandBenefitLedgerCommand.TargetProgressChange(); c.setPlanId(plan.getPlanId()); c.setDelta(amount); c.setAction(action); c.setRequestNo(requestNo); c.setSourceType(sourceType); c.setSourceNo(sourceNo); c.setReason(reason); ledger.changeTargetProgress(c); }
    private MemberTargetPlanSnapshot active(Long id) { MemberTargetPlanSnapshot plan = targetPlanQuery.findById(id); require(plan != null && "IN_PROGRESS".equals(plan.getStatus()), "TARGET计划不存在或不可操作"); return plan; }
    private static MemberTargetReceiptVO receiptResult(OmsMemberTargetReceipt receipt) { MemberTargetReceiptVO r = new MemberTargetReceiptVO(); r.setReceiptNo(receipt.getReceiptNo()); r.setAmount(receipt.getAmount()); return r; }
    private static boolean text(String v) { return v != null && !v.trim().isEmpty(); }
    private static boolean positive(BigDecimal v) { return v != null && v.compareTo(BigDecimal.ZERO) > 0; }
    private static BigDecimal scale(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private static void require(boolean ok, String message) { if (!ok) throw new BaseException(message); }
}
