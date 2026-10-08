package com.money.dto.memberbenefit;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class MemberBenefitTierDTO {
    private Long id;
    @NotBlank(groups = Create.class) private String brandId;
    @NotBlank(groups = Create.class) private String tierCode;
    @NotBlank(groups = Create.class) private String tierName;
    @NotNull(groups = Create.class) private BigDecimal configuredAmount;
    @NotBlank(groups = Create.class) private String pricingLevelCode;
    @NotNull(groups = Create.class) private Integer rankValue;
    @NotNull(groups = Create.class) private Boolean enabled;
    @NotNull(groups = Create.class) private Integer sortNo;
    private String remark;
    public interface Create { }
}
