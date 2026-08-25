package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiChatRequest {

    /** 会话 id，不传则新建会话 */
    private Long conversationId;

    /** 消息内容 */
    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息不能超过 2000 字")
    private String message;

    /** 图片 URL 列表（最多 4 张，带图时自动使用视觉模型） */
    @Size(max = 4, message = "最多携带 4 张图片")
    private List<String> imageUrls;

    /** 引用提问的笔记 id（可选，已发布笔记） */
    private Long noteId;

    /** 指定模型（可选） */
    private String model;
}
