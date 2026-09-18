package com.money.contract.goods;

import java.util.List;

/**
 * GMS 为历史 POS 商品搜索路由提供的只读查询契约。
 *
 * <p>该契约刻意独立于 {@link PosGoodsCatalogQuery}，以保留旧路由的字段和搜索口径。</p>
 */
public interface LegacyPosGoodsSearchQuery {

    List<LegacyPosGoodsSearchSnapshot> searchForLegacyPos(String keyword);
}
