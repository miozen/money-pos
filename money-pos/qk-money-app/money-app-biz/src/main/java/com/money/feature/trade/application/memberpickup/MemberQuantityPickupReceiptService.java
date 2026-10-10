package com.money.feature.trade.application.memberpickup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.CheckoutGoodsQuery;
import com.money.contract.goods.CheckoutGoodsSnapshot;
import com.money.dto.pos.MemberQuantityPickupReceiptVO;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberQuantityPickup;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberQuantityPickupItem;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberQuantityPickupItemMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberQuantityPickupMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds a TRADE-owned, non-sales receipt snapshot after a completed quantity pickup. */
@Service
@RequiredArgsConstructor
public class MemberQuantityPickupReceiptService {
    private final OmsMemberQuantityPickupMapper pickupMapper;
    private final OmsMemberQuantityPickupItemMapper itemMapper;
    private final CheckoutGoodsQuery checkoutGoodsQuery;

    public MemberQuantityPickupReceiptVO receipt(String pickupNo) {
        OmsMemberQuantityPickup pickup = pickupMapper.selectOne(new LambdaQueryWrapper<OmsMemberQuantityPickup>()
                .eq(OmsMemberQuantityPickup::getPickupNo, pickupNo));
        if (pickup == null || !"COMPLETED".equals(pickup.getStatus())) throw new BaseException("会员提货单不存在或未完成");
        List<OmsMemberQuantityPickupItem> items = itemMapper.selectList(new LambdaQueryWrapper<OmsMemberQuantityPickupItem>()
                .eq(OmsMemberQuantityPickupItem::getPickupNo, pickupNo));
        if (items.isEmpty()) throw new BaseException("会员提货单明细不存在");
        Map<Long, CheckoutGoodsSnapshot> goods = checkoutGoodsQuery.findByIds(items.stream().map(OmsMemberQuantityPickupItem::getGoodsId).collect(Collectors.toList()));
        MemberQuantityPickupReceiptVO result = new MemberQuantityPickupReceiptVO();
        result.setPickupNo(pickup.getPickupNo()); result.setMemberId(pickup.getMemberId()); result.setPickupTime(pickup.getCreateTime());
        result.setOperatorName(SecurityContextHolder.getContext().getAuthentication() == null ? "System" : SecurityContextHolder.getContext().getAuthentication().getName());
        result.setLines(items.stream().map(item -> line(item, goods.get(item.getGoodsId()))).collect(Collectors.toList()));
        return result;
    }

    private MemberQuantityPickupReceiptVO.Line line(OmsMemberQuantityPickupItem item, CheckoutGoodsSnapshot goods) {
        if (goods == null) throw new BaseException("提货商品不存在");
        MemberQuantityPickupReceiptVO.Line line = new MemberQuantityPickupReceiptVO.Line();
        line.setGoodsName(goods.getName()); line.setGoodsBarcode(goods.getBarcode()); line.setQuantity(item.getQuantity()); return line;
    }
}
