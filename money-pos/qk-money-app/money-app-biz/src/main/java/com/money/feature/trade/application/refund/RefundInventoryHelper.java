package com.money.feature.trade.application.checkout.refund;

import com.money.constant.BizErrorStatus;
import com.money.entity.OmsOrderDetail;
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

@Component
@RequiredArgsConstructor
public class RefundInventoryHelper {

    private final OmsOrderDetailMapper omsOrderDetailMapper;
    private final GoodsStockFacade goodsStockFacade;

    public void processFullOrderInventory(String orderNo, List<OmsOrderDetail> details) {
        List<RefundStockLine> lines = new ArrayList<>();
        for (OmsOrderDetail detail : details) {
            int canReturn = detail.getQuantity() - Optional.ofNullable(detail.getReturnQuantity()).orElse(0);
            if (canReturn > 0) {
                markOrderDetailReturned(detail, canReturn);
                lines.add(toRefundStockLine(detail, canReturn));
            }
        }
        RefundStockRequest request = new RefundStockRequest();
        request.setOrderNo(orderNo);
        request.setLines(lines);
        goodsStockFacade.restoreForRefund(request);
    }

    public BigDecimal processPartialReturnInventory(String orderNo, OmsOrderDetail detail, int returnQty) {
        markOrderDetailReturned(detail, returnQty);
        RefundStockRequest request = new RefundStockRequest();
        request.setOrderNo(orderNo);
        request.setLines(Collections.singletonList(toRefundStockLine(detail, returnQty)));
        return goodsStockFacade.restoreForRefund(request);
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
