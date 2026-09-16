package com.money.feature.trade.application.boundary.facade;

import com.money.contract.goods.RefundStockCommand;
import com.money.contract.goods.RefundStockCommandHandler;
import com.money.contract.goods.SaleStockCommand;
import com.money.contract.goods.SaleStockCommandHandler;
import com.money.contract.goods.StockMutationLine;
import com.money.feature.trade.application.boundary.facade.dto.RefundStockLine;
import com.money.feature.trade.application.boundary.facade.dto.RefundStockRequest;
import com.money.feature.trade.application.boundary.facade.dto.SaleStockLine;
import com.money.feature.trade.application.boundary.facade.dto.SaleStockRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/** TRADE adapter for GMS-owned inventory commands. */
@Service
@RequiredArgsConstructor
public class GoodsStockFacade {

    private final SaleStockCommandHandler saleStockCommandHandler;
    private final RefundStockCommandHandler refundStockCommandHandler;

    public void deductForSale(SaleStockRequest request) {
        SaleStockCommand command = new SaleStockCommand();
        command.setOrderNo(request.getOrderNo());
        command.setLines(request.getLines().stream().map(this::toStockMutationLine).collect(Collectors.toList()));
        saleStockCommandHandler.handle(command);
    }

    public BigDecimal restoreForRefund(RefundStockRequest request) {
        RefundStockCommand command = new RefundStockCommand();
        command.setOrderNo(request.getOrderNo());
        command.setLines(request.getLines().stream().map(this::toStockMutationLine).collect(Collectors.toList()));
        return refundStockCommandHandler.handle(command);
    }

    private StockMutationLine toStockMutationLine(SaleStockLine line) {
        StockMutationLine commandLine = baseLine(line.getGoodsId(), line.getGoodsName(), line.getGoodsBarcode(),
                line.getQuantity(), line.getPurchasePrice());
        commandLine.setCombo(line.getCombo());
        return commandLine;
    }

    private StockMutationLine toStockMutationLine(RefundStockLine line) {
        return baseLine(line.getGoodsId(), line.getGoodsName(), line.getGoodsBarcode(), line.getQuantity(), line.getPurchasePrice());
    }

    private StockMutationLine baseLine(
            Long goodsId, String goodsName, String goodsBarcode, Integer quantity, BigDecimal purchasePrice) {
        StockMutationLine commandLine = new StockMutationLine();
        commandLine.setGoodsId(goodsId);
        commandLine.setGoodsName(goodsName);
        commandLine.setGoodsBarcode(goodsBarcode);
        commandLine.setQuantity(quantity);
        commandLine.setPurchasePrice(purchasePrice);
        return commandLine;
    }
}
