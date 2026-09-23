package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ums_member_quantity_right")
public class UmsMemberQuantityRight {
    @TableId(type = IdType.AUTO) private Long id;
    private Long memberId;
    private String brandId;
    private Long goodsId;
    private String sourceOrderNo;
    private Long sourceOrderDetailId;
    private String createRequestNo;
    private Integer grantedQuantity;
    private Integer pickedQuantity;
    private Integer remainingQuantity;
    private String status;
    private Long tenantId;
}
