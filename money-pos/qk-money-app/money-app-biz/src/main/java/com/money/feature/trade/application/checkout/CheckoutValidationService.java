package com.money.feature.trade.application.checkout;

import cn.hutool.core.util.StrUtil;
import com.money.constant.PayMethodEnum;
import com.money.dto.OmsOrderDetail.OmsOrderDetailDTO;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.entity.GmsGoods;
import com.money.entity.UmsMember;
import com.money.service.GmsGoodsService;
import com.money.feature.ums.application.member.UmsMemberService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 🌟 结算流水线第一关：安检员
 * 纯读取操作。负责拦截一切非法参数，并将核实后的会员和商品实体装入公文包。
 */
@Service
@RequiredArgsConstructor
public class CheckoutValidationService {

    private final UmsMemberService umsMemberService;
    private final GmsGoodsService gmsGoodsService;

    public void validate(CheckoutContext context) {
        SettleAccountsDTO dto = context.getRequest();

        // ================= 1. 基础防呆校验 =================
        if (StrUtil.isBlank(dto.getReqId())) throw new BaseException("缺少请求单号");
        if (dto.getOrderDetail() == null || dto.getOrderDetail().isEmpty()) throw new BaseException("购物车明细为空");

        for (OmsOrderDetailDTO item : dto.getOrderDetail()) {
            if (item.getGoodsId() == null) throw new BaseException("含有无效商品ID");
            if (item.getQuantity() == null || item.getQuantity() <= 0) throw new BaseException("商品数量必须大于0");
        }

        if (dto.getUsedCouponCount() != null && dto.getUsedCouponCount() < 0) throw new BaseException("优惠券数量不可为负数");
        if (dto.getManualDiscountAmount() != null && dto.getManualDiscountAmount().compareTo(BigDecimal.ZERO) < 0) throw new BaseException("手工优惠金额不可为负数");
        if (dto.getPayments() == null || dto.getPayments().isEmpty()) throw new BaseException("支付明细为空");

        // ================= 2. 会员身份核验 =================
        UmsMember verifiedMember = null;
        if (dto.getMember() != null) {
            verifiedMember = umsMemberService.getById(dto.getMember());
            if (verifiedMember == null) throw new BaseException("【风控拦截】系统中未找到对应的会员卡信息");
        }
        // 👉 将查到的真实会员装入公文包
        context.setMember(verifiedMember);

        // ================= 3. 支付方式风控拦截 =================
        boolean hasValidPayment = false;
        for (SettleAccountsDTO.PaymentItem p : dto.getPayments()) {
            if (StrUtil.isBlank(p.getPayMethodCode())) throw new BaseException("缺少支付渠道编码");
            if (p.getPayAmount() != null && p.getPayAmount().compareTo(BigDecimal.ZERO) < 0) throw new BaseException("单笔支付金额不可为负数");
            if (p.getPayAmount() != null && p.getPayAmount().compareTo(BigDecimal.ZERO) > 0) hasValidPayment = true;

            PayMethodEnum methodEnum = PayMethodEnum.fromCode(p.getPayMethodCode());
            if (methodEnum == null) throw new BaseException("【风控拦截】不支持的未知支付方式: " + p.getPayMethodCode());

            // 聚合支付必须带标签（如微信、支付宝），非聚合绝对不能带标签
            if (methodEnum == PayMethodEnum.AGGREGATE && StrUtil.isBlank(p.getPayTag())) {
                throw new BaseException("【风控拦截】聚合扫码支付(AGGREGATE)必须明确具体的渠道标签(如 WECHAT)");
            }
            if (methodEnum != PayMethodEnum.AGGREGATE && StrUtil.isNotBlank(p.getPayTag())) {
                throw new BaseException("【风控拦截】非聚合支付(如现金/余额)不允许传递附加渠道标签");
            }

            // 余额支付必须是会员
            if (methodEnum.isAsset() && verifiedMember == null) {
                throw new BaseException("【风控拦截】非会员禁止使用会员余额/资产支付");
            }
        }
        if (!hasValidPayment) throw new BaseException("请录入大于 0 的有效支付金额");

        // ================= 4. 商品物资档案核实验真 =================
        List<Long> goodsIds = dto.getOrderDetail().stream().map(OmsOrderDetailDTO::getGoodsId).collect(Collectors.toList());
        Map<Long, GmsGoods> goodsMap = gmsGoodsService.listByIds(goodsIds).stream().collect(Collectors.toMap(GmsGoods::getId, g -> g));

        for (Long gid : goodsIds) {
            if (!goodsMap.containsKey(gid)) {
                throw new BaseException("【异常拦截】商品不存在或已从商品库中彻底删除: " + gid);
            }
        }
        // 👉 将查到的真实商品档案装入公文包，后面查移动均价全靠它
        context.setGoodsMap(goodsMap);
    }
}
