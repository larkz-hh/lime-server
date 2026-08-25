package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiMessageResponse {

    private Long id;

    /** user / assistant */
    private String role;

    private String content;

    /** 消息携带的图片 URL 列表（可能为空） */
    private List<String> images;

    /** 消息引用提问的笔记 id（App 端渲染引用卡片用，可为 null） */
    private Long noteId;

    private LocalDateTime createTime;
}
