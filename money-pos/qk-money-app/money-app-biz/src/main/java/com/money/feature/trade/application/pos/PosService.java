package com.money.feature.trade.application.pos;

import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.pos.PosGoodsVO;
import com.money.dto.pos.PosMemberVO;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO; // 🌟 必须导入这个新创建的 VO
import com.money.entity.PosCouponRule;
import java.util.List;

public interface PosService {
    List<PosGoodsVO> listGoods(String barcode);
    List<PosMemberVO> listMember(String member);
    List<PosCouponRule> getValidCouponRules();

    // 🌟 返回值已修改为增强版 VO
    SettleResultVO settleAccounts(SettleAccountsDTO dto);
}
