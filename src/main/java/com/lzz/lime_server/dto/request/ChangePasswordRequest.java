package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    // 原密码，与 code 二选一
    private String oldPassword;

    // 邮箱验证码，与 oldPassword 二选一
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度为 6-32 位")
    private String newPassword;
}
