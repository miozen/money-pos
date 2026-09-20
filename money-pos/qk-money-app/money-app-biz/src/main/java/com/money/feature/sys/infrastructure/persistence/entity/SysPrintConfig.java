package com.money.feature.sys.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.money.mb.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_print_config")
public class SysPrintConfig extends BaseEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String shopName;
    private String shopPhone;
    private String shopAddress; // 🌟 新增：门店地址
    private String headerMsg;
    private String footerMsg;
    private Boolean autoPrint;
    private Boolean openDrawer;
}
