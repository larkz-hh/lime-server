package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiConversationResponse {

    /** 客户端生成的会话 id（UUID） */
    private String id;

    /** 会话标题（首条消息截断） */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
