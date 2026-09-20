package com.money.feature.sys.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 *
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
@Getter
@Setter
@Schema(description = "")
@TableName("provinces")
public class Provinces {

    private String districtId;

    private String province;

    private String city;

    private String cityGeocode;

    private String district;

    private String districtGeocode;

    private String lon;

    private String lat;

}
