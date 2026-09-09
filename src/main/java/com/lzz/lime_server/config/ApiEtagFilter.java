package com.lzz.lime_server.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Component
public class ApiEtagFilter extends ShallowEtagHeaderFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 跳过 SSE 流式响应
        return uri.contains("/api/notifications/subscribe")
                || uri.equals("/api/ai/chat")
                || uri.equals("/api/ai/write/assist");
    }
}
