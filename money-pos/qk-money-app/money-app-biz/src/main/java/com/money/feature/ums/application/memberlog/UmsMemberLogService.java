package com.money.feature.ums.application.memberlog;

import com.baomidou.mybatisplus.extension.service.IService;
import com.money.entity.UmsMemberLog;
import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberLogQueryDTO;
import com.money.dto.UmsMember.UmsMemberLogVO;

/**
 * <p>
 * 会员资金流水 服务类
 * </p>
 */
public interface UmsMemberLogService extends IService<UmsMemberLog> {

    /**
     * 分页查询资金流水
     */
    PageVO<UmsMemberLogVO> list(UmsMemberLogQueryDTO queryDTO);


}
