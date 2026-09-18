package com.money.dto.UmsMember;

import lombok.Data;

/** API DTO for the member-profile Top 20 purchased-goods ranking. */
@Data
public class MemberGoodsRankVO {
    private String goodsName;
    private Integer buyCount;

    public MemberGoodsRankVO(String goodsName, Integer buyCount) {
        this.goodsName = goodsName;
        this.buyCount = buyCount;
    }
}
