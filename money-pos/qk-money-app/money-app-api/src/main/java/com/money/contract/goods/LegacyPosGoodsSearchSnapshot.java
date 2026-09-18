package com.money.contract.goods;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 历史 {@code /gms/goods/pos-search} 响应所需的 GMS 商品快照。 */
public class LegacyPosGoodsSearchSnapshot {

    private final Long id;
    private final Long brandId;
    private final Long categoryId;
    private final String barcode;
    private final String name;
    private final String pinyin;
    private final String pic;
    private final String unit;
    private final String size;
    private final String description;
    private final BigDecimal purchasePrice;
    private final BigDecimal salePrice;
    private final BigDecimal vipPrice;
    private final BigDecimal coupon;
    private final Long stock;
    private final Long sales;
    private final String status;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;
    private final Integer isDiscountParticipable;
    private final Map<String, BigDecimal> levelPrices;
    private final Map<String, BigDecimal> levelCoupons;

    public LegacyPosGoodsSearchSnapshot(Long id, Long brandId, Long categoryId, String barcode, String name,
                                        String pinyin, String pic, String unit, String size, String description,
                                        BigDecimal purchasePrice, BigDecimal salePrice, BigDecimal vipPrice,
                                        BigDecimal coupon, Long stock, Long sales, String status,
                                        LocalDateTime createTime, LocalDateTime updateTime,
                                        Integer isDiscountParticipable, Map<String, BigDecimal> levelPrices,
                                        Map<String, BigDecimal> levelCoupons) {
        this.id = id;
        this.brandId = brandId;
        this.categoryId = categoryId;
        this.barcode = barcode;
        this.name = name;
        this.pinyin = pinyin;
        this.pic = pic;
        this.unit = unit;
        this.size = size;
        this.description = description;
        this.purchasePrice = purchasePrice;
        this.salePrice = salePrice;
        this.vipPrice = vipPrice;
        this.coupon = coupon;
        this.stock = stock;
        this.sales = sales;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.isDiscountParticipable = isDiscountParticipable;
        this.levelPrices = immutableCopy(levelPrices);
        this.levelCoupons = immutableCopy(levelCoupons);
    }

    private static Map<String, BigDecimal> immutableCopy(Map<String, BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, BigDecimal>(values));
    }

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public Long getCategoryId() { return categoryId; }
    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public String getPinyin() { return pinyin; }
    public String getPic() { return pic; }
    public String getUnit() { return unit; }
    public String getSize() { return size; }
    public String getDescription() { return description; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public BigDecimal getVipPrice() { return vipPrice; }
    public BigDecimal getCoupon() { return coupon; }
    public Long getStock() { return stock; }
    public Long getSales() { return sales; }
    public String getStatus() { return status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public Integer getIsDiscountParticipable() { return isDiscountParticipable; }
    public Map<String, BigDecimal> getLevelPrices() { return levelPrices; }
    public Map<String, BigDecimal> getLevelCoupons() { return levelCoupons; }
}
