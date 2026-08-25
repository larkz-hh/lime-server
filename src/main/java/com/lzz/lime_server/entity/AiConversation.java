package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话标题（首条消息截断） */
    private String title;

    /** 历史对话摘要（滚动压缩生成，可空） */
    private String summary;

    /** 已压缩到哪条消息 id（<= 该 id 的消息已并入摘要，组装上下文时跳过） */
    private Long summarizedUntilId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
