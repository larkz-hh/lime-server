package com.lzz.lime_server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 核心安全配置类
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 配置 HTTP 安全策略
     * 定义请求的拦截规则、会话管理策略以及自定义过滤器的位置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF 防御
                .csrf(AbstractHttpConfigurer::disable)
                // 会话管理：设置为无状态 (STATELESS)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 授权规则配置
                // 定义直接访问接口，必须登录后才能访问接口
                .authorizeHttpRequests(auth -> auth
                        // 放行 /api/auth/** 路径下的所有请求
                        .requestMatchers("/api/auth/**").permitAll()
                        // 以下只读接口无需登录即可浏览
                        // 首页信息流、视频流
                        .requestMatchers(HttpMethod.GET, "/api/notes/feed", "/api/notes/video-feed").permitAll()
                        // 笔记详情
                        .requestMatchers(HttpMethod.GET, "/api/notes/{id:\\d+}").permitAll()
                        // 指定用户的笔记/点赞/收藏列表；草稿可见性与列表隐私由服务层校验
                        .requestMatchers(HttpMethod.GET, "/api/notes/user/**").permitAll()
                        // 评论、回复、弹幕读取
                        .requestMatchers(HttpMethod.GET,
                                "/api/notes/{noteId:\\d+}/comments",
                                "/api/notes/{noteId:\\d+}/danmaku",
                                "/api/comments/{commentId:\\d+}/replies").permitAll()
                        // 搜索（笔记 / 用户 / 联想 / 热搜）
                        .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                        // 他人主页（按 id / handle / uid）
                        // 关注/粉丝列表属社交数据，匿名不可见，需登录后查看
                        .requestMatchers(HttpMethod.GET,
                                "/api/user/{userId:\\d+}",
                                "/api/user/byHandle/{handle}",
                                "/api/user/byUid/{uid}").permitAll()
                        // 其他所有请求都必须经过认证
                        .anyRequest().authenticated())
                // 注册 JWT 过滤器到过滤器链
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // 构建并返回安全过滤器链
        return http.build();
    }

    /**
     * 配置密码加密器
     * 注册一个 BCrypt 加密算法的 Bean。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
