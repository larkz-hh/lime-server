package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiConversationResponse {

    private Long id;

    /** 会话标题（首条消息截断） */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
