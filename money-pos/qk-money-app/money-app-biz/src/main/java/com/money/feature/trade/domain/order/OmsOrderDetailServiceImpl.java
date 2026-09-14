package com.money.feature.trade.domain.order;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.entity.OmsOrderDetail;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.feature.trade.domain.order.OmsOrderDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 订单明细表 服务实现类 (防御性高并发版)
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
@Service
@RequiredArgsConstructor
public class OmsOrderDetailServiceImpl extends ServiceImpl<OmsOrderDetailMapper, OmsOrderDetail> implements OmsOrderDetailService {

    @Override
    public List<OmsOrderDetail> listByOrderNo(String orderNo) {
        // 🌟 修复 A：空值短路拦截！
        // 绝对禁止 null 或空字符串打入数据库，防止生成导致全表扫描的畸形 SQL
        if (StrUtil.isBlank(orderNo)) {
            return Collections.emptyList();
        }

        // 🌟 性能备忘：此处极度依赖数据库针对 order_no 字段建立的 B-Tree 单列索引
        // 警告：请务必在 DB 层面确保 oms_order_detail.order_no 的字符集和排序规则
        // 与 oms_order.order_no 绝对一致（如同为 utf8mb4_general_ci），否则会发生隐式转换导致索引失效！
        return this.lambdaQuery()
                .eq(OmsOrderDetail::getOrderNo, orderNo)
                .list();
    }
}