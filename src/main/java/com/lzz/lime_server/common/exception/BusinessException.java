package com.lzz.lime_server.common.exception;

import com.lzz.lime_server.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常，在 Service 层主动抛出可预期的错误
 * 用户不存在、密码错误、无权限等
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }
}
