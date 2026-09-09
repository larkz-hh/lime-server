package com.lzz.lime_server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NotificationResponse;
import com.lzz.lime_server.entity.Notification;
import com.lzz.lime_server.mapper.NotificationMapper;
import com.lzz.lime_server.mq.InteractionEvent;
import com.lzz.lime_server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 站内通知服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final StringRedisTemplate redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    // 互动事件 topic
    private static final String TOPIC = "interaction-topic";
    // 消费幂等去重 key 前缀
    private static final String DEDUP_KEY_PREFIX = "notif:event:";

    // 在线用户的 SSE 连接
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 发布互动事件到 MQ
     */
    @Override
    public void notifyUser(Long senderId, Long receiverId, int type, Long noteId, Long commentId, String content) {
        if (receiverId == null || receiverId.equals(senderId)) return;

        InteractionEvent event = new InteractionEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setSenderId(senderId);
        event.setReceiverId(receiverId);
        event.setType(type);
        event.setNoteId(noteId);
        event.setCommentId(commentId);
        event.setContent(content);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEvent(event);
                }
            });
        } else {
            sendEvent(event);
        }
    }

    /**
     * 发送事件消息，失败仅记日志
     */
    private void sendEvent(InteractionEvent event) {
        try {
            rocketMQTemplate.convertAndSend(TOPIC, event);
        } catch (Exception e) {
            log.error("发送互动事件失败，eventId={}", event.getEventId(), e);
        }
    }

    /**
     * 消费互动事件。
     * Redis setnx 幂等去重后落库通知并推送未读数
     */
    @Override
    public void consumeEvent(InteractionEvent event) {
        if (event == null || event.getReceiverId() == null) return;

        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(DEDUP_KEY_PREFIX + event.getEventId(), "1", Duration.ofHours(24));
        if (!Boolean.TRUE.equals(first)) return;

        Notification n = new Notification();
        n.setReceiverId(event.getReceiverId());
        n.setSenderId(event.getSenderId());
        n.setType(event.getType());
        n.setNoteId(event.getNoteId());
        n.setCommentId(event.getCommentId());
        n.setContent(truncate(event.getContent(), 200));
        n.setIsRead(false);
        notificationMapper.insert(n);

        pushUnread(event.getReceiverId());
    }

    /**
     * 通知列表，游标分页
     */
    @Override
    public CursorPage<NotificationResponse> getNotifications(Long userId, Long cursor, int size, List<Integer> types) {
        List<NotificationMapper.NotificationRow> rows =
                notificationMapper.selectNotifications(userId, cursor, size + 1, types);
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<NotificationResponse> items = rows.stream().map(row -> {
            NotificationResponse r = new NotificationResponse();
            r.setId(row.getId());
            r.setType(row.getType());
            r.setNoteId(row.getNoteId());
            r.setCommentId(row.getCommentId());
            r.setContent(row.getContent());
            r.setIsRead(row.getIsRead());
            r.setCreateTime(row.getCreateTime());
            r.setSenderId(row.getSenderId());
            r.setSenderNickname(row.getSenderNickname());
            r.setSenderAvatar(row.getSenderAvatar());
            r.setNoteCover(row.getNoteCover());
            r.setParentCommentId(row.getParentCommentId());
            r.setReplyToContent(row.getReplyToContent());
            return r;
        }).toList();

        Long nextCursor = hasMore ? rows.getLast().getId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 未读通知数（总数）
     */
    @Override
    public long getUnreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    /**
     * 按信箱分组的未读数。
     *total / likeFav(1,2,6) / follow(5) / comment(3,4)
     */
    @Override
    public Map<String, Long> getUnreadGrouped(Long userId) {
        long total = 0, likeFav = 0, follow = 0, comment = 0;
        for (NotificationMapper.TypeUnreadRow row : notificationMapper.countUnreadByType(userId)) {
            long cnt = row.getCnt() != null ? row.getCnt() : 0;
            total += cnt;
            switch (row.getType()) {
                case Notification.TYPE_LIKE, Notification.TYPE_FAVORITE, Notification.TYPE_COMMENT_LIKE -> likeFav += cnt;
                case Notification.TYPE_FOLLOW -> follow += cnt;
                case Notification.TYPE_COMMENT, Notification.TYPE_REPLY -> comment += cnt;
                default -> {
                }
            }
        }
        return Map.of("total", total, "likeFav", likeFav, "follow", follow, "comment", comment);
    }

    /**
     * 标记单条已读
     */
    @Override
    public void markRead(Long userId, Long notificationId) {
        notificationMapper.markRead(notificationId, userId);
        pushUnread(userId);
    }

    /**
     * 全部标记已读
     */
    @Override
    public void markAllRead(Long userId, List<Integer> types) {
        notificationMapper.markAllRead(userId, types);
        pushUnread(userId);
    }

    /**
     * 删除单条通知
     */
    @Override
    public void deleteNotification(Long userId, Long notificationId) {
        notificationMapper.deleteById(notificationId, userId);
        pushUnread(userId);
    }

    /**
     * 清空当前用户全部通知
     */
    @Override
    public void clearNotifications(Long userId) {
        notificationMapper.clearAll(userId);
        pushUnread(userId);
    }

    /**
     * SSE 订阅
     * 注册连接、返回当前未读数，断线自动移除
     */
    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("unread").data(buildUnreadPayload(userId)));
        } catch (Exception ignored) {
        }
        return emitter;
    }

    /**
     * 强制旧设备下线：向该用户所有在线 SSE 连接推送 kick 并关闭。
     */
    @Override
    public void kickUser(Long userId) {
        List<SseEmitter> list = emitters.remove(userId);
        if (list == null || list.isEmpty()) return;
        String payload = "{\"message\":\"您的账号已在其他设备登录\"}";
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("kick").data(payload));
            } catch (Exception ignored) {
            }
            try {
                e.complete();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 推送最新分组未读数
     */
    private void pushUnread(Long userId) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;
        String payload = buildUnreadPayload(userId);
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("unread").data(payload));
            } catch (Exception ex) {
                list.remove(e);
            }
        }
    }

    /**
     * 将分组未读数序列化为 JSON，作为 SSE data
     */
    private String buildUnreadPayload(Long userId) {
        try {
            return objectMapper.writeValueAsString(getUnreadGrouped(userId));
        } catch (JsonProcessingException e) {
            return "{\"total\":" + getUnreadCount(userId) + "}";
        }
    }

    /**
     * 截断摘要文本到指定长度
     */
    private String truncate(String content, int maxLen) {
        if (content == null || content.length() <= maxLen) return content;
        return content.substring(0, maxLen);
    }

    /**
     * 移除指定连接
     */
    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    /**
     * 心跳保活
     */
    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    list.remove(e);
                }
            }
        });
    }
}
