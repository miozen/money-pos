package com.money.contract.goods;

import java.util.Collection;
import java.util.Map;

/** Narrow GMS query for translating category IDs used in external report display. */
public interface GoodsCategoryNameQuery {
    Map<String, String> findNamesByIds(Collection<String> categoryIds);
}
