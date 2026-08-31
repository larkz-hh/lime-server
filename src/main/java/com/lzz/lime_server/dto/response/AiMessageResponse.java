package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiMessageResponse {

    /** 服务端内部消息 id */
    private Long id;

    /** 客户端生成的消息幂等键 */
    private String clientId;

    /** user / assistant */
    private String role;

    /** streaming / done / failed */
    private String status;

    private String content;

    /** 消息携带的图片 URL 列表（可能为空） */
    private List<String> images;

    /** 消息引用提问的笔记 id */
    private Long noteId;

    /** 引用笔记标题 */
    private String noteTitle;

    /** 引用笔记封面图 */
    private String noteCover;

    private LocalDateTime createTime;
}
