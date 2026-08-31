package com.lzz.lime_server.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 发给模型的一条消息（内部模型，支持多模态图片）
 */
@Data
@Builder
public class ChatMessage {

    /** 角色：system / user / assistant / tool */
    private String role;

    /** 文本内容（纯图片消息可为空） */
    private String content;

    /** 图片 URL 列表（可空；目标模型不支持视觉时由调用方置空） */
    private List<String> imageUrls;

    private String toolCallId;
    private List<AiToolCall> toolCalls;
}
