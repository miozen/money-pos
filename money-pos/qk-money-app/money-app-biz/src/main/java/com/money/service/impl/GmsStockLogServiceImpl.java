package com.money.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.mapper.GmsStockLogMapper;
import com.money.service.GmsStockLogService;
import org.springframework.stereotype.Service;

/**
 * 库存日志服务实现类 (提供 saveBatch 批量插入能力)
 */
@Service
public class GmsStockLogServiceImpl extends ServiceImpl<GmsStockLogMapper, GmsStockLog> implements GmsStockLogService {
}
