package com.money.feature.gms.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.money.dto.GmsGoods.TurnoverDataVO.WarningItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface GmsTurnoverMapper {

    // 🌟 数据洗缩版：
    // 1. 销量全部改用 GREATEST(quantity - return_quantity, 0) 绝对净销量！
    // 2. 排除套餐 (is_combo = 0)，防止单品补货队列被污染！
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT " +
            "  g.id AS goodsId, " +
            "  g.name AS goodsName, " +
            "  IFNULL(g.stock, 0) AS currentStock, " +
            "  IFNULL(SUM(CASE WHEN o.create_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) THEN GREATEST(od.quantity - IFNULL(od.return_quantity, 0), 0) ELSE 0 END), 0) AS sales30Days, " +
            "  IFNULL(SUM(CASE WHEN o.create_time >= DATE_SUB(CURDATE(), INTERVAL 90 DAY) THEN GREATEST(od.quantity - IFNULL(od.return_quantity, 0), 0) ELSE 0 END), 0) AS sales90Days, " +
            "  MAX(o.create_time) AS lastSaleTime " +
            "FROM gms_goods g " +
            "LEFT JOIN oms_order_detail od ON g.id = od.goods_id " +
            "LEFT JOIN oms_order o ON od.order_no = o.order_no AND o.status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') " +
            "WHERE g.status IN ('SALE', 'SOLD_OUT') " +
            "  AND IFNULL(g.is_combo, 0) = 0 " + // 👈 排除套餐
            "GROUP BY g.id, g.name, g.stock")
    List<WarningItemVO> scanAllGoodsTurnover();
}
