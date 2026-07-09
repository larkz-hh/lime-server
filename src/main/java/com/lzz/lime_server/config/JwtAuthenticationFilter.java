package com.lzz.lime_server.config;

import com.lzz.lime_server.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 身份认证过滤器
 * 继承 OncePerRequestFilter，确保每个 HTTP 请求在整个请求生命周期中只被该过滤器处理一次
 * 核心作用：拦截请求 -> 提取并验证 Token -> 将合法用户信息存入 Spring Security 上下文
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:token:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从请求头中提取 Token 字符串
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            // 检查黑名单
            Boolean isBlacklisted = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token);
            if (Boolean.TRUE.equals(isBlacklisted)) {
                filterChain.doFilter(request, response);
                return;
            }
            // 从 Token 中解析出用户 ID
            Long userId = jwtUtil.getUserIdFromToken(token);
            // 构建 Spring Security 的认证对象
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // 让请求继续向下传递给下一个过滤器或目标 Controller
        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求头中解析出真实的 Token 字符串
     * @param request 当前的 HTTP 请求
     * @return 提取出的 Token，如果不存在或格式不对则返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        // 获取 Authorization 请求头的值
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            // 截取掉 "Bearer " 前缀，返回真正的 Token 字符串
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
