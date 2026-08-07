package com.lzz.lime_server.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 获取客户端真实 IP 地址
 * <p>
 * 优先级：X-Forwarded-For → X-Real-IP → RemoteAddr
 * 反向代理（Nginx/CDN）会在 X-Forwarded-For 中追加 IP 链，取第一个即客户端 IP。
 * </p>
 */
public class IpUtil {

    private IpUtil() {}

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能是逗号分隔的 IP 链，第一个是真实客户端 IP
            int commaIndex = ip.indexOf(',');
            return commaIndex > 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        }
        // 解析 X-Real-IP
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();// RemoteAddr 兜底
    }
}
