package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单表。
 */
@Getter
@Setter
@TableName("oms_order")
@Schema(description = "订单表")
public class OmsOrder extends BaseEntity {

    @Schema(description = "订单号")
    private String orderNo;
    @Schema(description = "会员名")
    private String member;
    @Schema(description = "会员id")
    private Long memberId;
    @Schema(description = "vip单")
    private Boolean vip;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "联系方式")
    private String contact;
    @Schema(description = "省份")
    private String province;
    @Schema(description = "城市")
    private String city;
    @Schema(description = "地区")
    private String district;
    @Schema(description = "详细地址")
    private String address;
    @Schema(description = "总成本")
    private BigDecimal costAmount;
    @Schema(description = "零售价总额(吊牌价)")
    private BigDecimal retailAmount;
    @Schema(description = "会员价基准总额(成交底价)")
    private BigDecimal memberAmount;
    @Schema(description = "会员特权原值(零售价-会员价)")
    private BigDecimal privilegeAmount;
    @Schema(description = "真实会员券核销额(免收为0)")
    private BigDecimal actualCouponDeduct;
    @Schema(description = "店铺承担免收额(免收为特权原值)")
    private BigDecimal waivedCouponAmount;
    @Schema(description = "总价(为兼容老版本，统一等同于 retailAmount)")
    private BigDecimal totalAmount;
    @Schema(description = "抵用券(为兼容老版本，统一等同于 actualCouponDeduct)")
    private BigDecimal couponAmount;
    @Schema(description = "实付款")
    private BigDecimal payAmount;
    @Schema(description = "最终销售金额")
    private BigDecimal finalSalesAmount;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "支付时间")
    private LocalDateTime paymentTime;
    @Schema(description = "完成时间")
    private LocalDateTime completionTime;
    @Schema(description = "租户id")
    private Long tenantId;
    @Schema(description = "满减券抵扣金额")
    private BigDecimal useVoucherAmount;
    @Schema(description = "整单优惠")
    private BigDecimal manualDiscountAmount;
}
