package com.lzz.lime_server.controller;

import com.lzz.lime_server.ai.AiModelInfo;
import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.dto.request.AiChatCancelRequest;
import com.lzz.lime_server.dto.request.AiChatRequest;
import com.lzz.lime_server.dto.request.TranslateRequest;
import com.lzz.lime_server.dto.request.WriteAssistRequest;
import com.lzz.lime_server.dto.response.AiConversationResponse;
import com.lzz.lime_server.dto.response.AiMessageResponse;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.TranslateResponse;
import com.lzz.lime_server.service.AiChatService;
import com.lzz.lime_server.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 功能控制器：写作辅助 / 看图写文案、AI 聊天与会话管理。
 * 流式接口返回 SSE（text/event-stream），事件 data 为 JSON：
 * delta / done / error 三种 type。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiChatService aiChatService;

    /// 内置模型列表
    @GetMapping("/models")
    public Result<List<AiModelInfo>> getModels() {
        return Result.success(aiService.getModels());
    }

    /// 写作辅助 / 看图写文案，SSE 流式返回
    @PostMapping("/write/assist")
    public SseEmitter assist(@Valid @RequestBody WriteAssistRequest request) {
        return aiService.assist(currentUserId(), request);
    }

    /// AI 翻译（非流式）
    @PostMapping("/translate")
    public Result<TranslateResponse> translate(@Valid @RequestBody TranslateRequest request) {
        return Result.success(aiService.translate(currentUserId(), request));
    }

    /// AI 聊天（支持多轮与发图），SSE 流式返回
    @PostMapping("/chat")
    public SseEmitter chat(@Valid @RequestBody AiChatRequest request) {
        return aiChatService.chat(currentUserId(), request);
    }

    /// 打断一条正在生成的回复
    @PostMapping("/chat/cancel")
    public Result<Void> cancelChat(@Valid @RequestBody AiChatCancelRequest request) {
        aiChatService.cancel(currentUserId(), request);
        return Result.success();
    }

    /// 我的会话列表，游标分页（按会话 id 倒序）
    @GetMapping("/conversations")
    public Result<CursorPage<AiConversationResponse>> getConversations(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(aiChatService.getConversations(currentUserId(), cursor, size));
    }

    /// 会话历史消息，时间正序
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<AiMessageResponse>> getMessages(@PathVariable String conversationId) {
        return Result.success(aiChatService.getMessages(currentUserId(), conversationId));
    }

    /// 删除会话（本人）
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable String conversationId) {
        aiChatService.deleteConversation(currentUserId(), conversationId);
        return Result.success();
    }

    /// 删除会话中的单条消息（仅本人）
    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}")
    public Result<Void> deleteMessage(@PathVariable String conversationId, @PathVariable Long messageId) {
        aiChatService.deleteMessage(currentUserId(), conversationId, messageId);
        return Result.success();
    }

    /// 清空会话全部消息，保留会话（仅本人）
    @DeleteMapping("/conversations/{conversationId}/messages")
    public Result<Void> clearMessages(@PathVariable String conversationId) {
        aiChatService.clearMessages(currentUserId(), conversationId);
        return Result.success();
    }

    /// 从 Spring Security 上下文中获取当前已认证用户的 ID
    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
