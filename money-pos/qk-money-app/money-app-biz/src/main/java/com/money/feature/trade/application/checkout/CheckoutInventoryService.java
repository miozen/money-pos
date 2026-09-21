package com.money.feature.trade.application.checkout;

import com.money.contract.goods.CheckoutGoodsSnapshot;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.money.feature.trade.application.boundary.facade.GoodsStockFacade;
import com.money.feature.trade.application.boundary.facade.dto.SaleStockLine;
import com.money.feature.trade.application.boundary.facade.dto.SaleStockRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckoutInventoryService {

    private final GoodsStockFacade goodsStockFacade;

    public void deductStock(CheckoutContext context) {
        List<OmsOrderDetail> orderDetails = context.getOrderDetails();
        Map<Long, CheckoutGoodsSnapshot> goodsMap = context.getGoodsMap();
        SaleStockRequest request = new SaleStockRequest();
        request.setOrderNo(context.getOrder().getOrderNo());
        request.setLines(orderDetails.stream().map(detail -> toSaleStockLine(detail, goodsMap.get(detail.getGoodsId())))
                .collect(Collectors.toList()));
        goodsStockFacade.deductForSale(request);
    }

    private SaleStockLine toSaleStockLine(OmsOrderDetail detail, CheckoutGoodsSnapshot goods) {
        SaleStockLine line = new SaleStockLine();
        line.setGoodsId(detail.getGoodsId());
        line.setGoodsName(detail.getGoodsName());
        line.setGoodsBarcode(detail.getGoodsBarcode());
        line.setQuantity(detail.getQuantity());
        line.setPurchasePrice(detail.getPurchasePrice());
        line.setCombo(goods != null && goods.getIsCombo() != null && goods.getIsCombo() == 1);
        return line;
    }
}
