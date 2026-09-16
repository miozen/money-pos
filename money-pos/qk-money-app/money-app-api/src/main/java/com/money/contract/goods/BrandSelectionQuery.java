package com.money.contract.goods;

import java.util.List;

/** Read-only GMS brand choices for dynamic columns and import validation. */
public interface BrandSelectionQuery {

    List<BrandSelectionSnapshot> listBrandSelections();
}
