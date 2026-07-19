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
}
