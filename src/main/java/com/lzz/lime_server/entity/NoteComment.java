package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_comment")
public class NoteComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private Long userId;

    // null=一级评论，非null=回复（指向一级评论 id）
    private Long parentId;

    // 回复的目标用户 id，仅二级回复时有值
    private Long replyToUserId;

    private String content;

    private String voiceUrl;

    // 语音时长（秒），客户端录音后传入
    private Integer voiceDuration;

    private Integer likeCount;

    // 仅一级评论有效，回复数
    private Integer replyCount;

    // 热度分 = like_count + reply_count * 2
    private Integer hotScore;

    private String ipAddress;

    // IP 属地，发布时由 IpLocationService 解析后存入
    private String ipLocation;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
