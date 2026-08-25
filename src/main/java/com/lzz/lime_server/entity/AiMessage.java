package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant */
    private String role;

    private String content;

    /** 消息携带的图片 URL 列表（JSON 数组字符串，可空） */
    private String images;

    /** 用户引用提问的笔记 id（可空） */
    private Long noteId;

    /** 笔记上下文快照（JSON，发送时检索一次存入，后续轮次复用） */
    private String noteSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
