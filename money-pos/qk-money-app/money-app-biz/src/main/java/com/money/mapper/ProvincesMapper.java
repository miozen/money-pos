package com.money.mapper;

import com.money.entity.Provinces;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
public interface ProvincesMapper {

    @Select("SELECT district_id AS districtId, province, city, city_geocode AS cityGeocode, "
            + "district, district_geocode AS districtGeocode, lon, lat FROM provinces")
    List<Provinces> selectAll();
}
