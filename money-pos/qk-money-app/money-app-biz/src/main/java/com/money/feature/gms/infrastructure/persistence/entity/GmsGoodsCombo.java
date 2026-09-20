package com.money.feature.gms.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GmsGoodsCombo {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long comboGoodsId; // 套餐的ID
    private Long subGoodsId;   // 里面单品的ID
    private Integer subGoodsQty; // 单品给了几个
    private LocalDateTime createTime;
}
