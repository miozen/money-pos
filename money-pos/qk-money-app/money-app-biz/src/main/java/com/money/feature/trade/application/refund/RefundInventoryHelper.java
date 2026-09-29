package com.money.feature.trade.application.checkout.refund;

import com.money.constant.BizErrorStatus;
import com.money.contract.member.MemberQuantityRefundCommand;
import com.money.contract.member.MemberQuantityRefundCommandHandler;
import com.money.contract.member.MemberQuantityRefundResult;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.feature.trade.application.boundary.facade.GoodsStockFacade;
import com.money.feature.trade.application.boundary.facade.dto.RefundStockLine;
import com.money.feature.trade.application.boundary.facade.dto.RefundStockRequest;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RefundInventoryHelper {

    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final GoodsStockFacade goodsStockFacade;
    private final MemberQuantityRefundCommandHandler memberQuantityRefundCommandHandler;

    public void processFullOrderInventory(String requestNo, String orderNo, List<OmsOrderDetail> details) {
        Map<Long, MemberQuantityRefundResult> deferredResults = processDeferredQuantityRefund(requestNo, orderNo, details,
                detail -> detail.getQuantity() - Optional.ofNullable(detail.getReturnQuantity()).orElse(0));
        List<RefundStockLine> lines = new ArrayList<>();
        for (OmsOrderDetail detail : details) {
            int canReturn = detail.getQuantity() - Optional.ofNullable(detail.getReturnQuantity()).orElse(0);
            if (canReturn > 0 && !deferredResults.containsKey(detail.getId())) {
                markOrderDetailReturned(detail, canReturn);
                lines.add(toRefundStockLine(detail, canReturn));
            } else if (canReturn > 0) {
                markOrderDetailReturned(detail, canReturn);
            }
        }
        RefundStockRequest request = new RefundStockRequest();
        request.setOrderNo(orderNo);
        request.setLines(lines);
        goodsStockFacade.restoreForRefund(request);
    }

    public BigDecimal processPartialReturnInventory(String requestNo, String orderNo, OmsOrderDetail detail, int returnQty) {
        Map<Long, MemberQuantityRefundResult> deferredResults = processDeferredQuantityRefund(requestNo, orderNo,
                Collections.singletonList(detail), ignored -> returnQty);
        if (deferredResults.containsKey(detail.getId())) {
            markOrderDetailReturned(detail, returnQty);
            return BigDecimal.ZERO;
        }
        markOrderDetailReturned(detail, returnQty);
        RefundStockRequest request = new RefundStockRequest();
        request.setOrderNo(orderNo);
        request.setLines(Collections.singletonList(toRefundStockLine(detail, returnQty)));
        return goodsStockFacade.restoreForRefund(request);
    }

    private Map<Long, MemberQuantityRefundResult> processDeferredQuantityRefund(String requestNo, String orderNo, List<OmsOrderDetail> details,
                                                java.util.function.ToIntFunction<OmsOrderDetail> quantityResolver) {
        MemberQuantityRefundCommand command = new MemberQuantityRefundCommand();
        command.setRequestNo(requestNo);
        command.setSourceOrderNo(orderNo);
        command.setLines(details.stream().map(detail -> {
            MemberQuantityRefundCommand.Line line = new MemberQuantityRefundCommand.Line();
            line.setSourceOrderDetailId(detail.getId()); line.setQuantity(quantityResolver.applyAsInt(detail)); return line;
        }).filter(line -> line.getQuantity() > 0).collect(Collectors.toList()));
        if (command.getLines().isEmpty()) return Collections.emptyMap();
        Map<Long, MemberQuantityRefundResult> results = memberQuantityRefundCommandHandler.handle(command).stream()
                .collect(Collectors.toMap(MemberQuantityRefundResult::getSourceOrderDetailId, result -> result));
        List<RefundStockLine> physicalReturns = new ArrayList<>();
        for (OmsOrderDetail detail : details) {
            MemberQuantityRefundResult result = results.get(detail.getId());
            if (result == null) continue;
            int physicalQuantity = result.getPickedReturnQuantity() == null ? 0 : result.getPickedReturnQuantity();
            if (physicalQuantity > 0) physicalReturns.add(toRefundStockLine(detail, physicalQuantity));
        }
        goodsStockFacade.restoreForMemberPickupReturn(command.getRequestNo(), physicalReturns);
        return results;
    }

    private void markOrderDetailReturned(OmsOrderDetail detail, int returnQty) {
        int updatedRows = omsOrderDetailMapper.refundGoodsAtomically(detail.getId(), returnQty);
        if (updatedRows != 1) {
            throw new BaseException(BizErrorStatus.POS_REFUND_QTY_INVALID).withData("请求退数量:" + returnQty);
        }
    }

    private RefundStockLine toRefundStockLine(OmsOrderDetail detail, int quantity) {
        RefundStockLine line = new RefundStockLine();
        line.setGoodsId(detail.getGoodsId());
        line.setGoodsName(detail.getGoodsName());
        line.setGoodsBarcode(detail.getGoodsBarcode());
        line.setQuantity(quantity);
        line.setPurchasePrice(detail.getPurchasePrice());
        return line;
    }
}
