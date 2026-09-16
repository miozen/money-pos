package com.money.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.money.dto.SysDictDetailDTO;
import com.money.entity.SysDictDetail;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author money
 * @since 2022-03-30
 */
public interface SysDictDetailService extends IService<SysDictDetail> {

    /**
     * 查询字典详情列表
     *
     * @param dict dict
     * @return {@link List}<{@link SysDictDetail}>
     */
    List<SysDictDetail> listByDict(String dict);

    /**
     * 返回字典值到中文描述的有序映射，供跨域只读场景使用，避免暴露字典持久化实体。
     */
    Map<String, String> getValueToCnDescMap(String dict);

    /**
     * 添加字典详情
     *
     * @param sysDictDetailDTO 系统字典详情dto
     */
    void add(SysDictDetailDTO sysDictDetailDTO);

    /**
     * 更新字典详情
     *
     * @param sysDictDetailDTO 系统字典详情dto
     */
    void updateById(SysDictDetailDTO sysDictDetailDTO);

    /**
     * 删除字典详情
     *
     * @param ids id
     */
    void deleteById(Collection<Long> ids);

    /**
     * 删除字典
     *
     * @param dictList 字典
     */
    void deleteByDict(Collection<String> dictList);

}
