package com.money.contract.goods;

import java.util.Collection;
import java.util.Map;

/** 为结账流程提供商品、库存可售和会员价快照的中立只读契约。 */
public interface CheckoutGoodsQuery {

    Map<Long, CheckoutGoodsSnapshot> findByIds(Collection<Long> goodsIds);
}
