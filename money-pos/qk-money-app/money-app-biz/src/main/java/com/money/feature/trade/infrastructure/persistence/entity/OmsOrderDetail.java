package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 订单明细表。 */
@Getter
@Setter
@TableName("oms_order_detail")
@Schema(description = "订单明细表")
public class OmsOrderDetail extends BaseEntity {

    @Schema(description = "订单号") private String orderNo;
    @Schema(description = "状态") private String status;
    @Schema(description = "商品id") private Long goodsId;
    @Schema(description = "商品条码") private String goodsBarcode;
    @Schema(description = "商品名称") private String goodsName;
    @Schema(description = "实际单价") private BigDecimal goodsPrice;
    @Schema(description = "数量") private Integer quantity;
    @Schema(description = "售价") private BigDecimal salePrice;
    /** 交易发生时的成本快照，落库后永不篡改。 */
    @Schema(description = "成本快照 (交易发生时的 avgCostPrice，落库后永不篡改)") private BigDecimal purchasePrice;
    @Schema(description = "会员价") private BigDecimal vipPrice;
    @Schema(description = "抵用券") private BigDecimal coupon;
    @Schema(description = "退货数量") private Integer returnQuantity;
    @Schema(description = "租户id") private Long tenantId;
    /** 品牌 ID 历史快照。 */
    private Long brandId;
    /** 分类 ID 历史快照。 */
    private Long categoryId;
    /** 分类名称历史快照。 */
    private String categoryName;
}
