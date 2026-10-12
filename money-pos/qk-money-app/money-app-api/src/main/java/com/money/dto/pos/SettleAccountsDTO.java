package com.money.dto.pos;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.money.dto.OmsOrderDetail.OmsOrderDetailDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

/**
 * 🌟 POS 结算请求数据传输对象
 * 已经过金融级重构：支持幂等键、强制免券开关、以及支付子标签
 */
@Data
@Schema(description = "POS结算请求参数")
public class SettleAccountsDTO {

    @Schema(description = "交易唯一请求号(幂等键)，由前端生成，用于防止网络抖动导致重复下单")
    private String reqId;

    @Schema(description = "关联会员ID，匿名购买可为空")
    private Long member;

    @Schema(description = "使用的满减券规则ID (Voucher)")
    private Long usedCouponRuleId;

    @Schema(description = "使用的满减券张数")
    private Integer usedCouponCount;

    @NotEmpty(message = "订单明细不能为空")
    @Schema(description = "订单商品明细列表")
    private List<OmsOrderDetailDTO> orderDetail;

    @Schema(description = "强制免收会员券开关：如果为true，则该单所有商品应扣除的会员券强制归零")
    private Boolean waiveCoupon;

    @Schema(description = "手工整单优惠金额")
    private BigDecimal manualDiscountAmount;

    @Schema(description = "收银员确认计入本单的TARGET计划ID；未传或为空时不计入任何计划")
    private List<Long> targetPlanIds;

    @Schema(description = "组合支付明细流水列表")
    private List<PaymentItem> payments;

    /**
     * 支付项明细 (🌟 V2.0 强约束映射版)
     */
    @Data
    @Schema(description = "支付项明细")
    public static class PaymentItem {

        @JsonProperty("payMethodCode")
        @JSONField(name = "payMethodCode")
        @Schema(description = "支付方式编码，如 CASH, WECHAT, ALIPAY, BALANCE")
        private String payMethodCode;

        @JsonProperty("payMethodName")
        @JSONField(name = "payMethodName")
        @Schema(description = "支付方式展示名称")
        private String payMethodName;

        // 🌟 核心定义：这里接收的必须是“原始录入金额（实收）”，不要前端算好的净额！
        @JsonProperty("payAmount")
        @JSONField(name = "payAmount")
        @Schema(description = "该项支付的具体金额 (前端录入的原始实收)")
        private BigDecimal payAmount;

        @JsonProperty("payTag")
        @JSONField(name = "payTag")
        @Schema(description = "支付子标签或第三方流水号 (如支付渠道返回的交易号)")
        private String payTag;
    }
}
