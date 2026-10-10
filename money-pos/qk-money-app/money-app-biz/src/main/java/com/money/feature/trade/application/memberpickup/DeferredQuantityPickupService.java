package com.money.feature.trade.application.memberpickup;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.CheckoutGoodsQuery;
import com.money.contract.goods.CheckoutGoodsSnapshot;
import com.money.contract.goods.MemberPickupStockCommand;
import com.money.contract.goods.MemberPickupStockCommandHandler;
import com.money.contract.goods.StockMutationLine;
import com.money.contract.member.MemberQuantityPickupCommand;
import com.money.contract.member.MemberQuantityPickupCommandHandler;
import com.money.contract.member.MemberQuantityRightQuery;
import com.money.contract.member.MemberQuantityRightSnapshot;
import com.money.dto.pos.DeferredQuantityPickupDTO;
import com.money.dto.pos.DeferredQuantityPickupPreviewDTO;
import com.money.dto.pos.DeferredQuantityPickupPreviewVO;
import com.money.dto.pos.DeferredQuantityPickupVO;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberQuantityPickup;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberQuantityPickupItem;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberQuantityPickupItemMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberQuantityPickupMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeferredQuantityPickupService {
    private final OmsMemberQuantityPickupMapper pickupMapper;
    private final OmsMemberQuantityPickupItemMapper pickupItemMapper;
    private final MemberQuantityRightQuery quantityRightQuery;
    private final MemberQuantityPickupCommandHandler quantityPickupCommandHandler;
    private final CheckoutGoodsQuery checkoutGoodsQuery;
    private final MemberPickupStockCommandHandler memberPickupStockCommandHandler;

    /** Validates the current right snapshot only; no right, stock or pickup record is mutated here. */
    @Transactional(readOnly = true)
    public DeferredQuantityPickupPreviewVO preview(DeferredQuantityPickupPreviewDTO dto) {
        if (dto == null || dto.getMemberId() == null || dto.getLines() == null || dto.getLines().isEmpty()) {
            throw new BaseException("会员提货预览请求不完整");
        }
        HashSet<Long> uniqueRightIds = new HashSet<>();
        for (DeferredQuantityPickupPreviewDTO.Line line : dto.getLines()) {
            if (line == null || line.getRightId() == null || line.getQuantity() == null || line.getQuantity() <= 0
                    || !uniqueRightIds.add(line.getRightId())) {
                throw new BaseException("提货数量不合法或重复");
            }
        }
        List<Long> rightIds = dto.getLines().stream().map(DeferredQuantityPickupPreviewDTO.Line::getRightId).collect(Collectors.toList());
        List<MemberQuantityRightSnapshot> rights = quantityRightQuery.findAvailableForPickup(dto.getMemberId(), rightIds);
        if (rights.size() != dto.getLines().size()) throw new BaseException("存在不可提货的数量权益");
        Map<Long, MemberQuantityRightSnapshot> rightMap = rights.stream().collect(Collectors.toMap(MemberQuantityRightSnapshot::getRightId, right -> right));
        Map<Long, CheckoutGoodsSnapshot> goodsMap = checkoutGoodsQuery.findByIds(rights.stream().map(MemberQuantityRightSnapshot::getGoodsId).collect(Collectors.toList()));
        DeferredQuantityPickupPreviewVO result = new DeferredQuantityPickupPreviewVO();
        result.setLines(dto.getLines().stream().map(line -> previewLine(line, rightMap, goodsMap)).collect(Collectors.toList()));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeferredQuantityPickupVO pickup(DeferredQuantityPickupDTO dto) {
        if (dto == null || dto.getMemberId() == null || dto.getReqId() == null || dto.getReqId().trim().isEmpty()
                || dto.getLines() == null || dto.getLines().isEmpty()) {
            throw new BaseException("会员提货请求不完整");
        }
        HashSet<Long> uniqueRightIds = new HashSet<>();
        for (DeferredQuantityPickupDTO.Line line : dto.getLines()) {
            if (line == null || line.getRightId() == null || line.getQuantity() == null || line.getQuantity() <= 0
                    || !uniqueRightIds.add(line.getRightId())) {
                throw new BaseException("提货权益明细不合法或重复");
            }
        }
        OmsMemberQuantityPickup existing = pickupMapper.selectOne(new LambdaQueryWrapper<OmsMemberQuantityPickup>()
                .eq(OmsMemberQuantityPickup::getRequestNo, dto.getReqId()));
        if (existing != null) return result(existing.getPickupNo());
        List<Long> rightIds = dto.getLines().stream().map(DeferredQuantityPickupDTO.Line::getRightId).collect(Collectors.toList());
        List<MemberQuantityRightSnapshot> rights = quantityRightQuery.findAvailableForPickup(dto.getMemberId(), rightIds);
        if (rights.size() != dto.getLines().size()) throw new BaseException("存在不可提货的数量权益");
        Map<Long, MemberQuantityRightSnapshot> rightMap = rights.stream().collect(Collectors.toMap(MemberQuantityRightSnapshot::getRightId, right -> right));
        Map<Long, CheckoutGoodsSnapshot> goodsMap = checkoutGoodsQuery.findByIds(rights.stream().map(MemberQuantityRightSnapshot::getGoodsId).collect(Collectors.toList()));
        String pickupNo = "MP" + IdUtil.getSnowflakeNextIdStr();
        OmsMemberQuantityPickup pickup = new OmsMemberQuantityPickup();
        pickup.setPickupNo(pickupNo); pickup.setRequestNo(dto.getReqId()); pickup.setMemberId(dto.getMemberId()); pickup.setStatus("COMPLETED");
        try {
            pickupMapper.insert(pickup);
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            OmsMemberQuantityPickup concurrent = pickupMapper.selectOne(new LambdaQueryWrapper<OmsMemberQuantityPickup>()
                    .eq(OmsMemberQuantityPickup::getRequestNo, dto.getReqId()));
            if (concurrent != null) return result(concurrent.getPickupNo());
            throw ignored;
        }
        MemberQuantityPickupCommand rightCommand = new MemberQuantityPickupCommand();
        rightCommand.setMemberId(dto.getMemberId()); rightCommand.setPickupNo(pickupNo); rightCommand.setRequestNo(dto.getReqId());
        rightCommand.setLines(dto.getLines().stream().map(line -> toRightLine(line, rightMap)).collect(Collectors.toList()));
        quantityPickupCommandHandler.handle(rightCommand);
        MemberPickupStockCommand stockCommand = new MemberPickupStockCommand(); stockCommand.setPickupNo(pickupNo);
        stockCommand.setLines(dto.getLines().stream().map(line -> toStockLine(line, rightMap, goodsMap)).collect(Collectors.toList()));
        memberPickupStockCommandHandler.handle(stockCommand);
        for (DeferredQuantityPickupDTO.Line line : dto.getLines()) {
            MemberQuantityRightSnapshot right = rightMap.get(line.getRightId());
            OmsMemberQuantityPickupItem item = new OmsMemberQuantityPickupItem(); item.setPickupNo(pickupNo);
            item.setQuantityRightId(right.getRightId()); item.setGoodsId(right.getGoodsId()); item.setQuantity(line.getQuantity()); pickupItemMapper.insert(item);
        }
        return result(pickupNo);
    }

    private MemberQuantityPickupCommand.Line toRightLine(DeferredQuantityPickupDTO.Line line, Map<Long, MemberQuantityRightSnapshot> rights) {
        MemberQuantityRightSnapshot right = rights.get(line.getRightId());
        if (right == null || line.getQuantity() == null || line.getQuantity() <= 0 || right.getRemainingQuantity() < line.getQuantity()) throw new BaseException("数量权益不足");
        MemberQuantityPickupCommand.Line result = new MemberQuantityPickupCommand.Line(); result.setRightId(line.getRightId()); result.setQuantity(line.getQuantity()); return result;
    }
    private DeferredQuantityPickupPreviewVO.Line previewLine(DeferredQuantityPickupPreviewDTO.Line line,
                                                               Map<Long, MemberQuantityRightSnapshot> rights,
                                                               Map<Long, CheckoutGoodsSnapshot> goodsMap) {
        MemberQuantityRightSnapshot right = rights.get(line.getRightId());
        if (right == null || right.getRemainingQuantity() == null || right.getRemainingQuantity() < line.getQuantity()) {
            throw new BaseException("数量权益不足");
        }
        CheckoutGoodsSnapshot goods = goodsMap.get(right.getGoodsId());
        if (goods == null) throw new BaseException("提货商品不存在");
        DeferredQuantityPickupPreviewVO.Line result = new DeferredQuantityPickupPreviewVO.Line();
        result.setRightId(right.getRightId()); result.setGoodsId(right.getGoodsId()); result.setGoodsName(goods.getName());
        result.setGrantedQuantity(right.getGrantedQuantity()); result.setPickedQuantity(right.getPickedQuantity());
        result.setPickupQuantity(line.getQuantity()); result.setRemainingQuantity(right.getRemainingQuantity());
        result.setRemainingAfterPickup(right.getRemainingQuantity() - line.getQuantity());
        return result;
    }
    private StockMutationLine toStockLine(DeferredQuantityPickupDTO.Line line, Map<Long, MemberQuantityRightSnapshot> rights, Map<Long, CheckoutGoodsSnapshot> goodsMap) {
        CheckoutGoodsSnapshot goods = goodsMap.get(rights.get(line.getRightId()).getGoodsId());
        if (goods == null) throw new BaseException("提货商品不存在");
        StockMutationLine result = new StockMutationLine(); result.setGoodsId(goods.getId()); result.setGoodsName(goods.getName()); result.setGoodsBarcode(goods.getBarcode());
        result.setQuantity(line.getQuantity()); result.setPurchasePrice(goods.getPurchasePrice()); result.setCombo(goods.getIsCombo() != null && goods.getIsCombo() == 1); return result;
    }
    private DeferredQuantityPickupVO result(String pickupNo) { DeferredQuantityPickupVO result = new DeferredQuantityPickupVO(); result.setPickupNo(pickupNo); return result; }
}
