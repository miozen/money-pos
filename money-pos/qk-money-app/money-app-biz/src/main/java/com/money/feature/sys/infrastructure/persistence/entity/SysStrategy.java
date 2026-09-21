package com.money.feature.sys.infrastructure.persistence.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
@Data @TableName("sys_strategy") @Schema(description="全局经营策略参数表")
public class SysStrategy {
 @TableId(type=IdType.AUTO) private Long id; private BigDecimal trafficOrderThreshold; private BigDecimal trafficValueThreshold; private Integer turnoverLeadTime; private Integer turnoverTargetDays; private Integer deadStockDays; private Integer weeklyAnalysisDays; private Integer monthlyAnalysisDays; private Long tenantId;
}
