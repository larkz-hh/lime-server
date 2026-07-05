package com.lzz.lime_server.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/// 状态码枚举
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在");

    private final int code;
    private final String message;
}
