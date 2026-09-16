package com.money.contract.goods;

import java.util.List;

/** 为 POS 商品搜索提供目录和会员价快照的中立只读契约。 */
public interface PosGoodsCatalogQuery {

    List<PosGoodsCatalogSnapshot> searchForPos(String keyword);
}
