package com.money.contract.goods;

import java.math.BigDecimal;
import java.util.Map;

/** 结账核验、试算、订单归档和库存命令所需的商品快照。 */
public class CheckoutGoodsSnapshot {

    private final Long id;
    private final Long brandId;
    private final Long categoryId;
    private final String categoryName;
    private final String barcode;
    private final String name;
    private final BigDecimal purchasePrice;
    private final BigDecimal avgCostPrice;
    private final BigDecimal salePrice;
    private final BigDecimal vipPrice;
    private final Long stock;
    private final Integer isDiscountParticipable;
    private final Integer isCombo;
    private final Map<String, BigDecimal> levelPrices;
    private final Map<String, BigDecimal> levelCoupons;

    public CheckoutGoodsSnapshot(Long id, Long brandId, Long categoryId, String categoryName, String barcode, String name,
                                 BigDecimal purchasePrice, BigDecimal avgCostPrice, BigDecimal salePrice, BigDecimal vipPrice,
                                 Long stock, Integer isDiscountParticipable, Integer isCombo,
                                 Map<String, BigDecimal> levelPrices, Map<String, BigDecimal> levelCoupons) {
        this.id = id;
        this.brandId = brandId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.barcode = barcode;
        this.name = name;
        this.purchasePrice = purchasePrice;
        this.avgCostPrice = avgCostPrice;
        this.salePrice = salePrice;
        this.vipPrice = vipPrice;
        this.stock = stock;
        this.isDiscountParticipable = isDiscountParticipable;
        this.isCombo = isCombo;
        this.levelPrices = levelPrices;
        this.levelCoupons = levelCoupons;
    }

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public Long getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public BigDecimal getAvgCostPrice() { return avgCostPrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public BigDecimal getVipPrice() { return vipPrice; }
    public Long getStock() { return stock; }
    public Integer getIsDiscountParticipable() { return isDiscountParticipable; }
    public Integer getIsCombo() { return isCombo; }
    public Map<String, BigDecimal> getLevelPrices() { return levelPrices; }
    public Map<String, BigDecimal> getLevelCoupons() { return levelCoupons; }
}
