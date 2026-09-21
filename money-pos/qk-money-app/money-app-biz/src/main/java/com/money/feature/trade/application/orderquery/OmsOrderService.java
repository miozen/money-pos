package com.money.feature.trade.application.orderquery;

import com.baomidou.mybatisplus.extension.service.IService;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsOrderVO;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.web.vo.PageVO;

/**
 * 🌟 订单大总管 (V8.0 轻拆分版)
 * 职责：基础CRUD、订单列表分页、详情组装、找零计算。
 * 注意：退款请调用 OmsOrderRefundService；报表请调用 OmsSalesAnalysisService。
 */
public interface OmsOrderService extends IService<OmsOrder> {

    /**
     * 订单分页列表
     */
    PageVO<OmsOrderVO> list(OmsOrderQueryDTO queryDTO);

    /**
     * 根据 ID 获取订单详情
     */
    OrderDetailVO getOrderDetail(Long id);

    /**
     * 根据单号获取订单详情
     */
    OrderDetailVO getOrderDetailByNo(String orderNo);
}
