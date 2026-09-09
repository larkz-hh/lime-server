package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NotificationResponse;
import com.lzz.lime_server.mq.InteractionEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface NotificationService {

    // 发布一条互动事件到 MQ
    void notifyUser(Long senderId, Long receiverId, int type, Long noteId, Long commentId, String content);

    // 消费互动事件，幂等落库通知并推送未读数
    void consumeEvent(InteractionEvent event);

    // 通知列表
    CursorPage<NotificationResponse> getNotifications(Long userId, Long cursor, int size, List<Integer> types);

    // 未读数（总数）
    long getUnreadCount(Long userId);

    // 按信箱分组的未读数
    Map<String, Long> getUnreadGrouped(Long userId);

    // 标记单条已读
    void markRead(Long userId, Long notificationId);

    // 全部标记已读（types 为空则全部，传 types 只清对应类型，供按信箱已读）
    void markAllRead(Long userId, List<Integer> types);

    // 删除单条通知
    void deleteNotification(Long userId, Long notificationId);

    // 清空当前用户全部通知
    void clearNotifications(Long userId);

    // SSE 订阅（实时推送未读数）
    SseEmitter subscribe(Long userId);

    // kick 事件 reason，客户端据此区分场景
    // login_elsewhere：异地登录，弹下线提醒并清登录态
    String REASON_LOGIN_ELSEWHERE = "login_elsewhere";
    // password_changed：改密码后的旧会话清理，客户端静默处理，用新 token 重连即可
    String REASON_PASSWORD_CHANGED = "password_changed";

    /**
     * 向该用户所有在线 SSE 连接推送 kick 并关闭。
     * data 为 JSON：{"reason":"...","message":"..."}，reason 见上方常量。
     *
     * @param userId 用户 ID
     * @param reason 下线场景
     */
    void kickUser(Long userId, String reason);
}
