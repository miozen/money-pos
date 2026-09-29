package com.money.feature.ums.application.memberasset;

import com.money.contract.member.MemberBalancePaymentCommand;
import com.money.contract.member.MemberBalancePaymentCommandHandler;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UMS implementation intentionally avoids order consumption metrics for business-receipt payments. */
@Service
@RequiredArgsConstructor
class MemberBalancePaymentCommandService implements MemberBalancePaymentCommandHandler {
    private final UmsMemberAssetService memberAssetService;

    @Override @Transactional(rollbackFor = Exception.class)
    public void handle(MemberBalancePaymentCommand command) {
        if (command == null || command.getMemberId() == null || command.getReceiptNo() == null || command.getRequestNo() == null
                || command.getAmount() == null || command.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BaseException("会员余额凭证支付命令不完整");
        }
        if (command.isRefund()) memberAssetService.addBalance(command.getMemberId(), command.getAmount(), command.getReceiptNo(), "业务凭证退款");
        else memberAssetService.deductBalance(command.getMemberId(), command.getAmount(), command.getReceiptNo(), "业务凭证支付扣除");
    }
}
