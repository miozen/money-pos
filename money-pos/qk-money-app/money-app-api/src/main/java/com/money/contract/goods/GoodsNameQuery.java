package com.money.contract.goods;

import java.util.Collection;
import java.util.Map;

/** Narrow GMS query for translating goods IDs used in another feature's display. */
public interface GoodsNameQuery {

    Map<Long, String> findNamesByGoodsIds(Collection<Long> goodsIds);
}
