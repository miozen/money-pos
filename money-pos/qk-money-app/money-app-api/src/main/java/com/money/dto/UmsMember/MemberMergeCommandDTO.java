package com.money.dto.UmsMember;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** Explicit, manually confirmed duplicate-member merge command. */
@Data
@Schema(description = "重复会员合并请求")
public class MemberMergeCommandDTO {
    @NotBlank @Size(max = 64)
    @Schema(description = "调用方生成的幂等请求号")
    private String requestNo;
    @NotNull @Schema(description = "来源会员 ID；合并后停用")
    private Long sourceMemberId;
    @NotNull @Schema(description = "保留会员 ID")
    private Long targetMemberId;
    @NotBlank @Size(max = 255)
    @Schema(description = "人工确认的合并原因")
    private String reason;
}
