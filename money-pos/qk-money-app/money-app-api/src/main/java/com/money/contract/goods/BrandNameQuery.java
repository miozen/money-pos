package com.money.contract.goods;

import java.util.Collection;
import java.util.Map;

/** Narrow GMS query for translating brand IDs used in another feature's display. */
public interface BrandNameQuery {

    Map<String, String> findNamesByIds(Collection<String> brandIds);
}
