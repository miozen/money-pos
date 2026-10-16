package com.money.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ChangeUsernameDTO {

    @Schema(description = "新登录名")
    @NotBlank(message = "新登录名不允许为空")
    @Size(min = 2, max = 20, message = "登录名长度在 2 到 20 个字符")
    private String username;
}
