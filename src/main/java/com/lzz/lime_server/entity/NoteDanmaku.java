package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 弹幕实体
 * <p>弹幕 = 带时间轴的轻量评论，独立于评论区 note_comment，不计入笔记评论数。</p>
 */
@Data
@TableName("note_danmaku")
public class NoteDanmaku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private Long userId;

    /** 弹幕文字 */
    private String content;

    /** 弹幕出现时间点（毫秒，相对视频开头） */
    private Long videoTimeMs;

    /** 弹幕颜色（#RRGGBB），null 时客户端用默认白色 */
    private String color;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
