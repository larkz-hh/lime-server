package com.lzz.lime_server.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;


/**
 * JWT 工具类
 * 主要负责 Token 的生成、解析、验证以及提取用户信息
 * 采用 Spring 单例模式，在整个应用生命周期中只有一个实例
 */
@Component
public class JwtUtil {

    /**
     * 从配置文件中读取 JWT 签名密钥
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 访问令牌的过期时间
     */
    @Value("${jwt.expire}")
    private long expire;

    /**
     * 刷新令牌的过期时间
     */
    @Value("${jwt.refresh-expire}")
    private long refreshExpire;

    /**
     * HMAC-SHA 算法所需的密钥对象
     * 使用 @PostConstruct 在 Bean 初始化时生成，避免每次生成 Token 时重复转换，提升性能
     */
    private SecretKey secretKey;

    /**
     * 初始化方法：在 Spring 容器完成属性注入后自动执行
     * 将字符串类型的密钥转换为 JJWT 库要求的 SecretKey 对象
     */
    @PostConstruct
    public void init() {
        // 使用 UTF-8 编码将字符串转为字节数组，生成适用于 HMAC-SHA 算法的密钥
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token (访问令牌)
     * @param userId 当前登录用户的唯一标识
     * @return 生成的 JWT 字符串
     */
    public String generateAccessToken(Long userId) {
        return buildToken(userId, expire * 1000);
    }

    /**
     * 生成 Refresh Token (刷新令牌)
     * @param userId 当前登录用户的唯一标识
     * @return 生成的 JWT 字符串
     */
    public String generateRefreshToken(Long userId) {
        return buildToken(userId, refreshExpire * 1000);
    }

    /**
     * 构建 Token 的核心私有方法
     * @param userId 用户ID，作为 Token 的 subject (主题)
     * @param expireMillis 过期时间，单位：毫秒
     * @return 签名并压缩后的 JWT 字符串
     */
    private String buildToken(Long userId, long expireMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                // 将用户ID转为字符串，存入 Token 的 subject 字段
                .subject(String.valueOf(userId))
                // 设置 Token 的签发时间
                .issuedAt(new Date(now))
                // 设置 Token 的过期时间 (当前时间 + 配置的过期毫秒数)
                .expiration(new Date(now + expireMillis))
                // 使用初始化好的密钥对 Token 进行数字签名，防止被篡改
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从 Token 中解析并提取用户 ID
     * @param token JWT 字符串
     * @return 解析出的用户 ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 验证 Token 是否合法有效
     * @param token JWT 字符串
     * @return true-合法有效；false-无效、被篡改或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 获取 Token 距离过期还剩多少毫秒
     * @param token JWT 字符串
     * @return 剩余的毫秒数
     */
    public long getRemainingExpireMillis(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }

    /**
     * 解析 Token 的核心私有方法
     * @param token JWT 字符串
     * @return Token 的载荷信息 (Claims)
     * @throws JwtException 当 Token 签名不匹配或已过期时抛出
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)// 设置用于验签的密钥
                .build() // 构建解析器
                .parseSignedClaims(token) // 解析并验证 Token 的签名
                .getPayload(); // 获取 Token 的载荷部分
    }
}
