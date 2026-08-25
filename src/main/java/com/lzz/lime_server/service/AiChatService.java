package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.AiChatRequest;
import com.lzz.lime_server.dto.response.AiConversationResponse;
import com.lzz.lime_server.dto.response.AiMessageResponse;
import com.lzz.lime_server.dto.response.CursorPage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiChatService {

    /** AI 聊天（SSE 流式，支持多轮与发图） */
    SseEmitter chat(Long userId, AiChatRequest request);

    /** 我的会话列表（游标分页，按更新时间倒序） */
    CursorPage<AiConversationResponse> getConversations(Long userId, String cursor, int size);

    /** 会话历史消息（时间正序） */
    List<AiMessageResponse> getMessages(Long userId, Long conversationId);

    /** 删除会话（仅本人） */
    void deleteConversation(Long userId, Long conversationId);

    /** 删除会话中的单条消息（仅本人） */
    void deleteMessage(Long userId, Long conversationId, Long messageId);

    /** 清空会话全部消息，保留会话（仅本人） */
    void clearMessages(Long userId, Long conversationId);
}
