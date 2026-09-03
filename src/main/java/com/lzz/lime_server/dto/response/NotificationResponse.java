package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

// 通知响应对象
@Data
public class NotificationResponse {

    private Long id;

    // 通知类型：1=点赞笔记 2=收藏 3=评论 4=回复 5=关注 6=点赞评论/回复
    private Integer type;

    private Long noteId;

    private Long commentId;

    // 通知摘要文本
    private String content;

    // 关联笔记封面图 URL
    private String noteCover;

    // 被回复的父评论 id
    private Long parentCommentId;

    // 被回复的原评论正文
    private String replyToContent;

    // 是否已读
    private Boolean isRead;

    private LocalDateTime createTime;

    // 触发者简要信息
    private Long senderId;
    private String senderNickname;
    private String senderAvatar;
}
