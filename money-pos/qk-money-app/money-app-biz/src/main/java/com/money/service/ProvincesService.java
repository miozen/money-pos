package com.money.service;

import com.money.dto.SelectVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
public interface ProvincesService {

    /**
     * 省份列表
     *
     * @return {@link List}<{@link SelectVO}>
     */
    List<SelectVO> listProvinces();

    /**
     * 城市列表
     *
     * @param province 省
     * @return {@link List}<{@link SelectVO}>
     */
    List<SelectVO> listCities(String province);

    /**
     * 列表区
     *
     * @param city 城市
     * @return {@link List}<{@link SelectVO}>
     */
    List<SelectVO> listDistricts(String city);
}
