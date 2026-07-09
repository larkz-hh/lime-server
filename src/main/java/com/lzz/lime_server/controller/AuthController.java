package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.dto.request.LoginRequest;
import com.lzz.lime_server.dto.request.RefreshTokenRequest;
import com.lzz.lime_server.dto.request.RegisterRequest;
import com.lzz.lime_server.dto.response.LoginResponse;
import com.lzz.lime_server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 负责处理用户注册、登录、登出以及 Token 刷新相关的 HTTP 请求
 * 所有接口的统一前缀为 /api/auth，且在 SecurityConfig 中已配置为全局放行
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册接口
     * 接收前端传来的注册信息，校验唯一性并创建新用户
     *
     * @param request 包含用户名、密码、邮箱等注册信息的请求体，已通过 @Valid 触发参数校验
     * @return 统一响应结果，成功时不包含业务数据
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /**
     * 用户登录接口
     * 校验用户名和密码，验证通过后颁发 Access Token 和 Refresh Token
     *
     * @param request 包含用户名和密码的请求体，已通过 @Valid 触发参数校验
     * @return 统一响应结果，包含双 Token 及过期时间信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 用户登出接口
     * 将当前用户的 Access Token 加入 Redis 黑名单，并清除 Refresh Token，实现即时下线
     *
     * @param userId 当前登录用户的唯一标识，由 Spring Security 从 SecurityContext 中自动解析并注入
     * @param bearerToken 请求头中的 Authorization 字段，格式为 "Bearer <token>"
     * @return 统一响应结果，成功时不包含业务数据
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Long userId,
                               @RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring("Bearer ".length());
        authService.logout(userId, token);
        return Result.success();
    }

    /**
     * 刷新 Token 接口
     * 使用长效的 Refresh Token 换取新的 Access Token，实现无感续期
     *
     * @param request 包含 Refresh Token 的请求体，已通过 @Valid 触发参数校验
     * @return 统一响应结果，包含新签发的双 Token 及过期时间信息
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request));
    }
}
