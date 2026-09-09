package com.lzz.lime_server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/** UserSig 下发结果，App 据此登录 IM SDK */
@Data
@AllArgsConstructor
public class ImUserSigResponse {

    private long sdkAppId;

    /** IM UserID：前缀 + 业务 userId */
    private String userId;

    private String userSig;

    /** 过期时间戳（秒），App 可在过期前刷新 */
    private long expire;
}
