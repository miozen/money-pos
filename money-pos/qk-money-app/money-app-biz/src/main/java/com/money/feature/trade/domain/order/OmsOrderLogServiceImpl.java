package com.money.feature.trade.domain.order;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderLog;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsOrderLogMapper;
import com.money.feature.trade.domain.order.OmsOrderLogService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 订单操作日志 服务实现类 (Immutable 强审计版)
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
@Service
@RequiredArgsConstructor
public class OmsOrderLogServiceImpl extends ServiceImpl<OmsOrderLogMapper, OmsOrderLog> implements OmsOrderLogService {

    @Override
    public List<OmsOrderLog> listByOrderId(Long orderId) {
        if (orderId == null) {
            return Collections.emptyList();
        }

        // 🌟 性能备忘：此处依赖数据库的 idx_order_id_time (order_id, create_time DESC) 复合索引
        return this.lambdaQuery()
                .eq(OmsOrderLog::getOrderId, orderId)
                .orderByDesc(OmsOrderLog::getCreateTime)
                .list();
    }

    // ==========================================
    // 🌟 核心重构：强制只读约束 (Immutable Data)
    // 订单日志是审计的绝对凭证，严禁在代码层面进行任何形式的物理修改和删除！
    // ==========================================

    @Override
    public boolean updateById(OmsOrderLog entity) {
        throw new BaseException("安全违规：订单审计日志严禁修改！");
    }

    @Override
    public boolean updateBatchById(Collection<OmsOrderLog> entityList) {
        throw new BaseException("安全违规：订单审计日志严禁修改！");
    }

    @Override
    public boolean removeById(Serializable id) {
        throw new BaseException("安全违规：订单审计日志严禁删除！");
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        throw new BaseException("安全违规：订单审计日志严禁删除！");
    }

    @Override
    public boolean removeByMap(java.util.Map<String, Object> columnMap) {
        throw new BaseException("安全违规：订单审计日志严禁删除！");
    }
}
