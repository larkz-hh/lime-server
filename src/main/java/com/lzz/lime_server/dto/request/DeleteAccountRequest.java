package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    // 当前账号密码，二次确认身份
    @NotBlank(message = "密码不能为空")
    private String password;
}
