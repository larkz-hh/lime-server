package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiChatRequest {

    /** 客户端生成的会话 id（UUID） */
    @NotBlank(message = "会话 id 不能为空")
    private String conversationId;

    /** 客户端生成的消息幂等键（UUID）*/
    @NotBlank(message = "消息幂等键不能为空")
    private String messageClientId;

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
