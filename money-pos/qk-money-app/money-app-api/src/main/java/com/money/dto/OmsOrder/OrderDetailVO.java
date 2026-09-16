package com.money.dto.OmsOrder;

import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.contract.member.MemberOrderProfileSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单详情VO (大一统解耦版)")
public class OrderDetailVO extends OmsOrderVO {

    @Schema(description = "订单明细列表")
    private List<OmsOrderDetailVO> orderDetails;

    @Schema(description = "会员余额实付金额")
    private BigDecimal balanceAmount;

    @Schema(description = "聚合扫码实付金额")
    private BigDecimal scanAmount;

    @Schema(description = "现金实付金额")
    private BigDecimal cashAmount;

    @Schema(description = "找零金额")
    private BigDecimal changeAmount;

    @Schema(description = "关联的会员完整档案")
    private MemberOrderProfileSnapshot memberInfo;

    @Schema(description = "系统操作审计日志")
    private List<OrderLogVO> orderLog;

    @Schema(description = "底层支付流水快照")
    private List<OrderPayVO> payments;

    @Data
    @Schema(description = "订单操作日志 (脱水版)")
    public static class OrderLogVO {
        private Long id;
        private String description;
        private String createBy;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(description = "订单支付流水 (脱水版)")
    public static class OrderPayVO {
        private Long id;
        private String payMethodName;
        private String payTag;
        private String payMethodCode;
        private BigDecimal payAmount;
        private LocalDateTime createTime;
    }

    @Schema(description="状态中文描述 (动态同步字典与枚举兜底)")
    private String statusDesc;
}
