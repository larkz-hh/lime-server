package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note")
public class Note {

    /** 笔记类型：图文 */
    public static final int TYPE_TEXT = 1;
    /** 笔记类型：视频 */
    public static final int TYPE_VIDEO = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    /** 笔记类型：1=图文, 2=视频 */
    private Integer noteType;

    // 0=草稿,1=已发布
    private Integer status;

    // 草稿来源笔记 id
    private Long sourceNoteId;

    private Integer likeCount;

    private Integer favCount;

    private Integer viewCount;

    private Integer commentCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updateTime;
}
