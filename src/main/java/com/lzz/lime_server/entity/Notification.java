package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

// 站内通知表
@Data
@TableName("notification")
public class Notification {

    // 通知类型常量
    public static final int TYPE_LIKE = 1;          // 点赞笔记
    public static final int TYPE_FAVORITE = 2;      // 收藏
    public static final int TYPE_COMMENT = 3;       // 评论
    public static final int TYPE_REPLY = 4;         // 回复
    public static final int TYPE_FOLLOW = 5;        // 关注
    public static final int TYPE_COMMENT_LIKE = 6;  // 点赞评论/回复

    @TableId(type = IdType.AUTO)
    private Long id;

    // 接收通知的用户 id
    private Long receiverId;

    // 触发通知的用户 id
    private Long senderId;

    // 通知类型：1=点赞笔记 2=收藏 3=评论 4=回复 5=关注 6=点赞评论/回复
    private Integer type;

    // 关联笔记 id
    private Long noteId;

    // 关联评论 id
    private Long commentId;

    // 通知摘要文本
    private String content;

    // 是否已读：0=未读 1=已读
    private Boolean isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
