package com.money.contract.goods;

import java.math.BigDecimal;
import java.util.Map;

/** POS 商品搜索和价格展示所需的商品目录快照。 */
public class PosGoodsCatalogSnapshot {

    private final Long id;
    private final String barcode;
    private final String name;
    private final Long brandId;
    private final BigDecimal purchasePrice;
    private final BigDecimal salePrice;
    private final BigDecimal vipPrice;
    private final BigDecimal coupon;
    private final Long stock;
    private final String status;
    private final Integer isDiscountParticipable;
    private final Integer isCombo;
    private final Map<String, BigDecimal> levelPrices;
    private final Map<String, BigDecimal> levelCoupons;

    public PosGoodsCatalogSnapshot(Long id, String barcode, String name, Long brandId, BigDecimal purchasePrice,
                                   BigDecimal salePrice, BigDecimal vipPrice, BigDecimal coupon, Long stock, String status,
                                   Integer isDiscountParticipable, Integer isCombo, Map<String, BigDecimal> levelPrices,
                                   Map<String, BigDecimal> levelCoupons) {
        this.id = id;
        this.barcode = barcode;
        this.name = name;
        this.brandId = brandId;
        this.purchasePrice = purchasePrice;
        this.salePrice = salePrice;
        this.vipPrice = vipPrice;
        this.coupon = coupon;
        this.stock = stock;
        this.status = status;
        this.isDiscountParticipable = isDiscountParticipable;
        this.isCombo = isCombo;
        this.levelPrices = levelPrices;
        this.levelCoupons = levelCoupons;
    }

    public Long getId() { return id; }
    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public Long getBrandId() { return brandId; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public BigDecimal getVipPrice() { return vipPrice; }
    public BigDecimal getCoupon() { return coupon; }
    public Long getStock() { return stock; }
    public String getStatus() { return status; }
    public Integer getIsDiscountParticipable() { return isDiscountParticipable; }
    public Integer getIsCombo() { return isCombo; }
    public Map<String, BigDecimal> getLevelPrices() { return levelPrices; }
    public Map<String, BigDecimal> getLevelCoupons() { return levelCoupons; }
}
