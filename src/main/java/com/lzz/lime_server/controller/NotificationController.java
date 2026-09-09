package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NotificationResponse;
import com.lzz.lime_server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 通知列表（游标分页），type 可选，按通知类型过滤
    @GetMapping("/api/notifications")
    public Result<CursorPage<NotificationResponse>> getNotifications(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type) {
        size = Math.min(size, 50);
        return Result.success(notificationService.getNotifications(currentUserId(), cursor, size, parseTypes(type)));
    }

    // 分组未读数：total / likeFav(赞和收藏) / follow(关注) / comment(评论与回复)
    @GetMapping("/api/notifications/unread-count")
    public Result<Map<String, Long>> getUnreadCount() {
        return Result.success(notificationService.getUnreadGrouped(currentUserId()));
    }

    // 标记单条通知已读
    @PutMapping("/api/notifications/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(currentUserId(), id);
        return Result.success();
    }

    // 全部标记已读，type 可选：不带 = 全部信箱，带 = 只清对应类型
    @PutMapping("/api/notifications/read-all")
    public Result<Void> markAllRead(@RequestParam(required = false) String type) {
        notificationService.markAllRead(currentUserId(), parseTypes(type));
        return Result.success();
    }

    // 删除单条通知
    @DeleteMapping("/api/notifications/{id}")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(currentUserId(), id);
        return Result.success();
    }

    // 清空全部通知
    @DeleteMapping("/api/notifications/all")
    public Result<Void> clearNotifications() {
        notificationService.clearNotifications(currentUserId());
        return Result.success();
    }

    // SSE 订阅，实时推送未读数变化
    @GetMapping(value = "/api/notifications/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return notificationService.subscribe(currentUserId());
    }

    // 从 Spring Security 上下文取当前登录用户 ID
    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // 解析 type 过滤参数
    private List<Integer> parseTypes(String type) {
        if (type == null || type.isBlank()) return null;
        try {
            List<Integer> types = Arrays.stream(type.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
            if (types.stream().anyMatch(t -> t < 1 || t > 6)) {
                throw new BusinessException("type 参数非法，可选值 1~6，多个用逗号分隔");
            }
            return types;
        } catch (NumberFormatException e) {
            throw new BusinessException("type 参数非法");
        }
    }
}
