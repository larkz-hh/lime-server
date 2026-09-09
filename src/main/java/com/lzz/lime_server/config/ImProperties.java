package com.lzz.lime_server.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class ImProperties {

    @Value("${im.sdk-app-id}")
    private long sdkAppId;

    /** Base64 编码 */
    @Value("${im.secret-key}")
    private String secretKey;

    /** 有效期（秒） */
    @Value("${im.user-sig-expire:604800}")
    private long userSigExpire;

    @Value("${im.user-id-prefix:lime_}")
    private String userIdPrefix;

    /** IM UserID 只允许 ASCII 可见字符，故拼上前缀再用于签发 UserSig */
    public String toImUserId(Long userId) {
        return userIdPrefix + userId;
    }
}
