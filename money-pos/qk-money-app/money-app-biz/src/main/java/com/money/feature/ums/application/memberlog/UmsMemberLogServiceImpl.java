package com.money.feature.ums.application.memberlog;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.dto.UmsMember.UmsMemberLogQueryDTO;
import com.money.dto.UmsMember.UmsMemberLogVO;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UmsMemberLogServiceImpl extends ServiceImpl<UmsMemberLogMapper, UmsMemberLog> implements UmsMemberLogService {

    private final UmsMemberMapper umsMemberMapper;

    @Override
    public PageVO<UmsMemberLogVO> list(UmsMemberLogQueryDTO queryDTO) {

        Long searchMemberId = queryDTO.getMemberId();

        // 手机号预查询保持不变，如果前端传了手机号，先找 ID
        if (StrUtil.isNotBlank(queryDTO.getPhone())) {
            UmsMember member = umsMemberMapper.selectOne(
                    new LambdaQueryWrapper<UmsMember>()
                            .eq(UmsMember::getPhone, queryDTO.getPhone())
                            .last("LIMIT 1")
            );
            searchMemberId = (member != null) ? member.getId() : -1L;
        }

        // 1. 极速分页查询 (利用新加的 idx_member_type_time 复合索引，速度起飞)
        Page<UmsMemberLog> page = this.lambdaQuery()
                .eq(searchMemberId != null, UmsMemberLog::getMemberId, searchMemberId)
                .eq(StrUtil.isNotBlank(queryDTO.getType()), UmsMemberLog::getType, queryDTO.getType())
                .eq(StrUtil.isNotBlank(queryDTO.getOperateType()), UmsMemberLog::getOperateType, queryDTO.getOperateType())
                .orderByDesc(UmsMemberLog::getCreateTime)
                .page(PageUtil.toPage(queryDTO));

        // 2. 转换 VO (直接映射，彻底砍掉 N+1 查库和内存 Map 组装！)
        return PageUtil.toPageVO(page, UmsMemberLogVO::new);
    }
}
