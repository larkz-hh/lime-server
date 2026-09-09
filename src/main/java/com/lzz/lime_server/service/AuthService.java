package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.LoginRequest;
import com.lzz.lime_server.dto.request.RefreshTokenRequest;
import com.lzz.lime_server.dto.request.RegisterRequest;
import com.lzz.lime_server.dto.request.SendCodeRequest;
import com.lzz.lime_server.dto.response.LoginResponse;

/**
 * 认证服务接口
 * <p>
 * 发验证码、注册、登录、登出、Token 刷新。
 * </p>
 */
public interface AuthService {

    void sendCode(SendCodeRequest request);

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(Long userId, String accessToken);

    LoginResponse refreshToken(RefreshTokenRequest request);

    /**
     * 改密成功后重新签发双 Token，当前会话无缝续用。
     * 凭证版本 +1 使旧 token 全部失效，并向旧 SSE 连接推送 password_changed 的 kick。
     */
    LoginResponse reissueTokensAfterPasswordChange(Long userId);
}
