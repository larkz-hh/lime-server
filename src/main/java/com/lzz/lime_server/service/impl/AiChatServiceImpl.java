package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lzz.lime_server.ai.AiModelSpec;
import com.lzz.lime_server.ai.AiProvider;
import com.lzz.lime_server.ai.AiRateLimiter;
import com.lzz.lime_server.ai.AiResponse;
import com.lzz.lime_server.ai.AiStreamCallback;
import com.lzz.lime_server.ai.AiSupport;
import com.lzz.lime_server.ai.AiToolCall;
import com.lzz.lime_server.ai.AiTools;
import com.lzz.lime_server.ai.ChatMessage;
import com.lzz.lime_server.ai.NoteContextLoader;
import com.lzz.lime_server.ai.NoteSnapshot;
import com.lzz.lime_server.ai.WeatherToolService;
import com.lzz.lime_server.ai.TavilySearchService;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.config.AiPrompts;
import com.lzz.lime_server.config.AiProperties;
import com.lzz.lime_server.dto.request.AiChatCancelRequest;
import com.lzz.lime_server.dto.request.AiChatRequest;
import com.lzz.lime_server.dto.response.AiConversationResponse;
import com.lzz.lime_server.dto.response.AiMessageResponse;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.entity.AiConversation;
import com.lzz.lime_server.entity.AiMessage;
import com.lzz.lime_server.mapper.AiConversationMapper;
import com.lzz.lime_server.mapper.AiMessageMapper;
import com.lzz.lime_server.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    /** 摘要压缩全局锁，同一时刻只做一个会话的压缩 */
    private static final Object SUMMARIZE_LOCK = new Object();

    private final AiProperties properties;
    private final AiPrompts prompts;
    private final AiSupport aiSupport;
    private final AiRateLimiter rateLimiter;
    private final AiProvider aiProvider;
    private final NoteContextLoader noteContextLoader;
    private final WeatherToolService weatherToolService;
    private final TavilySearchService tavilySearchService;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter chat(Long userId, AiChatRequest request) {
        String message = request.getMessage().trim();
        String conversationClientId = request.getConversationId();
        List<String> images = request.getImageUrls();
        boolean hasRequestImages = images != null && !images.isEmpty();

        // 引用笔记，发送时检索一次并快照存库，后续追问轮次直接复用快照
        NoteSnapshot noteSnapshot = null;
        if (request.getNoteId() != null) {
            noteSnapshot = noteContextLoader.load(request.getNoteId());
        }
        boolean needVision = hasRequestImages
                || (noteSnapshot != null && noteSnapshot.getImages() != null && !noteSnapshot.getImages().isEmpty());

        rateLimiter.check(userId, "chat", properties.getRateLimitPerMinute(), properties.getRateLimitPerDay());

        AiModelSpec spec = aiSupport.resolveSpec(request.getModel(), needVision);

        // 按客户端生成的会话 id 查找，不存在则新建
        AiConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getClientId, conversationClientId));
        if (conversation == null) {
            conversation = new AiConversation();
            conversation.setUserId(userId);
            conversation.setClientId(conversationClientId);
            conversation.setTitle(truncate(message, 50));
            conversationMapper.insert(conversation);
        }

        final Long internalConversationId = conversation.getId();

        // 用户消息落库
        final AiMessage userMsg;
        AiMessage existingUserMsg = messageMapper.selectOne(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getClientId, request.getMessageClientId()));
        if (existingUserMsg != null) {
            userMsg = existingUserMsg;
        } else {
            AiMessage msg = new AiMessage();
            msg.setConversationId(internalConversationId);
            msg.setClientId(request.getMessageClientId());
            msg.setRole("user");
            msg.setContent(message);
            msg.setImages(toJson(images));
            if (noteSnapshot != null) {
                msg.setNoteId(request.getNoteId());
                msg.setNoteSnapshot(toJson(noteSnapshot));
            }
            messageMapper.insert(msg);
            userMsg = msg;
        }

        // 滚动摘要压缩。失败不阻断聊天
        conversation = summarizeIfNeeded(internalConversationId);

        // 组装上下文（系统提示 + 历史摘要 + 最近 N 条原文，时间正序）
        List<ChatMessage> context = buildContext(conversation, spec.isSupportsVision());

        // 助手占位：生成开始即落库，边生成边更新（断线后重连续传/补齐用）
        AiMessage assistant = new AiMessage();
        assistant.setConversationId(internalConversationId);
        assistant.setRole("assistant");
        assistant.setContent("");
        assistant.setStatus("streaming");
        messageMapper.insert(assistant);
        final Long assistantId = assistant.getId();

        // 增量持久化
        final StringBuilder persistBuf = new StringBuilder();
        final long[] lastPersist = {System.currentTimeMillis()};
        Consumer<String> onDelta = text -> {
            persistBuf.append(text);
            long now = System.currentTimeMillis();
            if (now - lastPersist[0] > 800) {
                lastPersist[0] = now;
                // 仅当仍在 streaming 时写入
                messageMapper.update(null, new LambdaUpdateWrapper<AiMessage>()
                        .eq(AiMessage::getId, assistantId)
                        .eq(AiMessage::getStatus, "streaming")
                        .set(AiMessage::getContent, persistBuf.toString())
                        .set(AiMessage::getStatus, "streaming"));
            }
        };
        Consumer<String> onError = err -> {
            messageMapper.update(null, new LambdaUpdateWrapper<AiMessage>()
                    .eq(AiMessage::getId, assistantId)
                    .eq(AiMessage::getStatus, "streaming")
                    .set(AiMessage::getStatus, "failed"));
        };

        boolean enableSearch = request.getSearch() == null || request.getSearch();
        return startChatStream(spec, context, enableSearch, fullText -> {
            // 完成：仅当仍在 streaming 时写入全文并标记完成；已被打断(stopped)不覆盖、不推 done
            int updated = messageMapper.update(null, new LambdaUpdateWrapper<AiMessage>()
                    .eq(AiMessage::getId, assistantId)
                    .eq(AiMessage::getStatus, "streaming")
                    .set(AiMessage::getContent, fullText)
                    .set(AiMessage::getStatus, "done"));
            if (updated == 0) {
                return null;
            }

            // 消息数达到阈值时，异步生成简短标题
            maybeSummarizeTitleAsync(internalConversationId);

            // 刷新会话更新时间
            AiConversation update = new AiConversation();
            update.setId(internalConversationId);
            update.setUpdateTime(LocalDateTime.now());
            conversationMapper.updateById(update);

            ObjectNode done = objectMapper.createObjectNode();
            done.put("type", "done");
            done.put("conversationId", conversationClientId);
            done.put("userMessageId", userMsg.getId());
            done.put("assistantMessageId", assistantId);
            done.put("model", spec.getModel());
            return done.toString();
        }, onDelta, onError);
    }

    // ===== 工具调用（Function Calling） =====

    /** 带工具声明的 spec 副本 */
    private AiModelSpec withTools(AiModelSpec spec, boolean enableSearch) {
        return AiModelSpec.builder()
                .baseUrl(spec.getBaseUrl())
                .apiKey(spec.getApiKey())
                .model(spec.getModel())
                .supportsVision(spec.isSupportsVision())
                .authType(spec.getAuthType())
                .pathPrefix(spec.getPathPrefix())
                .enableThinking(spec.getEnableThinking())
                .extraBody(spec.getExtraBody())
                .tools(AiTools.chatTools(enableSearch))
                .build();
    }

    /**
     * 聊天流式入口：先让模型判断是否需要工具（非流式快速判断），
     * 需要则执行工具后继续流式输出最终回答；不需要则直接用第一轮结果。
     */
    private SseEmitter startChatStream(AiModelSpec spec, List<ChatMessage> context, boolean enableSearch,
                                       Function<String, String> doneEventBuilder,
                                       Consumer<String> onDelta, Consumer<String> onError) {
        SseEmitter emitter = new SseEmitter((properties.getReadTimeoutSeconds() + 15) * 1000L);
        Thread.ofVirtual().name("ai-chat").start(() -> {
            try {
                AiResponse first = aiProvider.chat(withTools(spec, enableSearch), context);
                List<AiToolCall> calls = first.getToolCalls();
                if (calls != null && !calls.isEmpty()) {
                    // 模型要调工具：先通知 App，再执行工具，之后继续流式
                    for (AiToolCall tc : calls) {
                        emitter.send(SseEmitter.event().data(toolEvent(tc.getName())));
                    }
                    List<ChatMessage> toolMessages = new ArrayList<>(context);
                    toolMessages.add(ChatMessage.builder()
                            .role("assistant")
                            .content(first.getContent() == null ? "" : first.getContent())
                            .toolCalls(calls)
                            .build());
                    for (AiToolCall tc : calls) {
                        toolMessages.add(ChatMessage.builder()
                                .role("tool")
                                .toolCallId(tc.getId())
                                .content(executeTool(tc))
                                .build());
                    }
                    streamToEmitter(emitter, spec, toolMessages, doneEventBuilder, onDelta, onError);
                } else {
                    // 无需工具：第一轮结果即为最终回答
                    String answer = first.getContent() == null ? "" : first.getContent();
                    String doneData = doneEventBuilder.apply(answer);
                    if (!answer.isEmpty()) {
                        emitter.send(SseEmitter.event().data(deltaEvent(answer)));
                    }
                    if (doneData != null) {
                        emitter.send(SseEmitter.event().data(doneData));
                    }
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("AI 聊天工具轮次失败", e);
                try {
                    onError.accept("AI 服务暂时不可用，请稍后重试");
                } catch (Exception ignored) {
                }
                try {
                    emitter.send(SseEmitter.event().data(errorEvent("AI 服务暂时不可用，请稍后重试")));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    /** 在既有 emitter 上流式输出模型回复（工具轮次之后的最终回答） */
    private void streamToEmitter(SseEmitter emitter, AiModelSpec spec, List<ChatMessage> messages,
                                 Function<String, String> doneEventBuilder,
                                 Consumer<String> onDelta, Consumer<String> onError) {
        StringBuilder full = new StringBuilder();
        aiProvider.streamChat(spec, messages, new AiStreamCallback() {
            @Override
            public void onDelta(String text) {
                full.append(text);
                try {
                    emitter.send(SseEmitter.event().data(deltaEvent(text)));
                    if (onDelta != null) {
                        onDelta.accept(text);
                    }
                } catch (Exception e) {
                    log.debug("SSE 发送失败（客户端可能已断开）：{}", e.getMessage());
                }
            }

            @Override
            public void onComplete() {
                try {
                    if (doneEventBuilder != null) {
                        String data = doneEventBuilder.apply(full.toString());
                        if (data != null) {
                            emitter.send(SseEmitter.event().data(data));
                        }
                    }
                    emitter.complete();
                } catch (Exception e) {
                    log.error("AI 流式收尾失败", e);
                }
            }

            @Override
            public void onError(String message) {
                try {
                    if (onError != null) {
                        onError.accept(message);
                    }
                    emitter.send(SseEmitter.event().data(errorEvent(message)));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });
    }

    /** 执行模型请求的工具调用 */
    private String executeTool(AiToolCall toolCall) {
        if ("get_weather".equals(toolCall.getName())) {
            return weatherToolService.fetchWeather(parseLocation(toolCall.getArguments()));
        }
        if ("search_web".equals(toolCall.getName())) {
            return tavilySearchService.search(parseQuery(toolCall.getArguments()));
        }
        return "未知工具：" + toolCall.getName();
    }

    /** 从工具参数 JSON 中解析 location */
    private String parseLocation(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(arguments);
            String location = node.path("location").asText("");
            return location.isBlank() ? null : location;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从工具参数 JSON 中解析 query */
    private String parseQuery(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(arguments);
            String query = node.path("query").asText("");
            return query.isBlank() ? null : query;
        } catch (Exception e) {
            return null;
        }
    }

    private String deltaEvent(String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "delta");
        node.put("content", text);
        return node.toString();
    }

    private String errorEvent(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "error");
        node.put("message", message);
        return node.toString();
    }

    private String toolEvent(String name) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "tool");
        node.put("name", name);
        return node.toString();
    }

    @Override
    public void cancel(Long userId, AiChatCancelRequest request) {
        AiMessage userMsg = messageMapper.selectOne(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getClientId, request.getMessageClientId()));
        if (userMsg == null) {
            return; // 消息不存在，忽略
        }
        AiConversation conversation = conversationMapper.selectById(userMsg.getConversationId());
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            return; // 无权或会话不存在，忽略
        }
        // 将该会话仍在流式生成的助手标记为停止
        messageMapper.update(null, new LambdaUpdateWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, userMsg.getConversationId())
                .eq(AiMessage::getRole, "assistant")
                .eq(AiMessage::getStatus, "streaming")
                .set(AiMessage::getStatus, "stopped")
                .set(request.getPartialContent() != null, AiMessage::getContent, request.getPartialContent()));
    }

    @Override
    public CursorPage<AiConversationResponse> getConversations(Long userId, String cursor, int size) {
        long cursorId = 0;
        if (cursor != null && !cursor.isBlank()) {
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new BusinessException("cursor 非法");
            }
        }
        List<AiConversation> list = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .lt(cursorId > 0, AiConversation::getId, cursorId)
                .orderByDesc(AiConversation::getId)
                .last("LIMIT " + (size + 1)));
        boolean hasMore = list.size() > size;
        List<AiConversation> page = hasMore ? list.subList(0, size) : list;
        List<AiConversationResponse> items = page.stream().map(c -> {
            AiConversationResponse r = new AiConversationResponse();
            r.setId(c.getClientId());
            r.setTitle(c.getTitle());
            r.setCreateTime(c.getCreateTime());
            r.setUpdateTime(c.getUpdateTime());
            return r;
        }).toList();
        Long next = hasMore ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, next, hasMore);
    }

    @Override
    public List<AiMessageResponse> getMessages(Long userId, String conversationId) {
        AiConversation conversation = requireOwnConversation(userId, conversationId);
        Long internalId = conversation.getId();
        List<AiMessage> list = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, internalId)
                .orderByAsc(AiMessage::getId));
        return list.stream().map(m -> {
            AiMessageResponse r = new AiMessageResponse();
            r.setId(m.getId());
            r.setClientId(m.getClientId());
            r.setRole(m.getRole());
            r.setStatus(m.getStatus());
            r.setContent(m.getContent());
            r.setImages(parseImages(m.getImages()));
            r.setNoteId(m.getNoteId());
            NoteSnapshot snapshot = parseSnapshot(m.getNoteSnapshot());
            if (snapshot != null) {
                r.setNoteTitle(snapshot.getTitle());
                List<String> imgs = snapshot.getImages();
                if (imgs != null && !imgs.isEmpty()) {
                    r.setNoteCover(imgs.get(0));
                }
            }
            r.setCreateTime(m.getCreateTime());
            return r;
        }).toList();
    }

    @Override
    public void deleteConversation(Long userId, String conversationId) {
        AiConversation conversation = requireOwnConversation(userId, conversationId);
        Long internalId = conversation.getId();
        conversationMapper.deleteById(internalId);
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getConversationId, internalId));
    }

    @Override
    public void deleteMessage(Long userId, String conversationId, Long messageId) {
        AiConversation conversation = requireOwnConversation(userId, conversationId);
        AiMessage message = messageMapper.selectById(messageId);
        if (message == null || !message.getConversationId().equals(conversation.getId())) {
            throw new BusinessException("消息不存在");
        }
        messageMapper.deleteById(messageId);
    }

    @Override
    public void clearMessages(Long userId, String conversationId) {
        AiConversation conversation = requireOwnConversation(userId, conversationId);
        Long internalId = conversation.getId();
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getConversationId, internalId));
        // 消息清空后，摘要与压缩游标重置
        conversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                .eq(AiConversation::getId, internalId)
                .set(AiConversation::getSummary, null)
                .set(AiConversation::getSummarizedUntilId, null));
    }


    /**
     * 滚动摘要压缩。未压缩消息数超过阈值时，把最早的一批压成摘要存到会话上。
     * 返回最新会话对象（含新摘要）；压缩失败记日志，不阻断本次聊天。
     */
    private AiConversation summarizeIfNeeded(Long conversationId) {
        synchronized (SUMMARIZE_LOCK) {
            AiConversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null) {
                return conversation;
            }
            long cursor = conversation.getSummarizedUntilId() == null ? 0L : conversation.getSummarizedUntilId();
            Long unsummarized = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversationId)
                    .gt(AiMessage::getId, cursor));
            if (unsummarized == null || unsummarized <= properties.getSummarizeTrigger()) {
                return conversation;
            }
            // 压缩后至少保留 chatContextMaxMessages 条原文，避免把近期上下文压掉
            int compressible = (int) (unsummarized - properties.getChatContextMaxMessages());
            if (compressible <= 0) {
                return conversation;
            }
            int batch = Math.min(compressible, properties.getSummarizeBatch());
            List<AiMessage> batchMessages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversationId)
                    .gt(AiMessage::getId, cursor)
                    .orderByAsc(AiMessage::getId)
                    .last("LIMIT " + batch));
            if (batchMessages.isEmpty()) {
                return conversation;
            }
            try {
                String newSummary = generateSummary(conversation.getSummary(), batchMessages);
                Long newCursor = batchMessages.get(batchMessages.size() - 1).getId();
                AiConversation update = new AiConversation();
                update.setId(conversationId);
                update.setSummary(newSummary);
                update.setSummarizedUntilId(newCursor);
                update.setUpdateTime(LocalDateTime.now());
                conversationMapper.updateById(update);
                conversation.setSummary(newSummary);
                conversation.setSummarizedUntilId(newCursor);
                log.info("会话 {} 滚动摘要完成：压缩 {} 条消息，游标 -> {}", conversationId, batchMessages.size(), newCursor);
            } catch (Exception e) {
                log.warn("会话 {} 摘要压缩失败，本次跳过", conversationId, e);
            }
            return conversation;
        }
    }

    /**
     * 用文本模型把一批消息压成摘要，已有旧摘要时在其基础上滚动更新。
     */
    private String generateSummary(String existingSummary, List<AiMessage> batchMessages) {
        StringBuilder dialogue = new StringBuilder();
        for (AiMessage m : batchMessages) {
            dialogue.append(m.getRole()).append(": ").append(truncate(m.getContent(), 500));
            NoteSnapshot snapshot = parseSnapshot(m.getNoteSnapshot());
            if (snapshot != null) {
                String title = snapshot.getTitle() == null || snapshot.getTitle().isBlank() ? "无标题" : snapshot.getTitle();
                dialogue.append("（引用了笔记《").append(title).append("》：")
                        .append(truncate(snapshot.getContent(), 200)).append("）");
            }
            dialogue.append("\n");
        }
        String instruction;
        if (existingSummary == null || existingSummary.isBlank()) {
            instruction = prompts.getSummarizeFirst().replace("{dialogue}", dialogue);
        } else {
            instruction = prompts.getSummarizeUpdate()
                    .replace("{existingSummary}", existingSummary)
                    .replace("{dialogue}", dialogue);
        }
        AiModelSpec spec = aiSupport.resolveLightSpec();
        AiResponse response = aiProvider.chat(spec, List.of(
                ChatMessage.builder().role("user").content(instruction).build()));
        String content = response.getContent() == null ? "" : response.getContent().trim();
        if (content.isBlank()) {
            throw new BusinessException("摘要生成为空");
        }
        return content;
    }

    /**
     * 回复完成后异步让 AI 判断是否需要更新会话标题。
     */
    private void maybeSummarizeTitleAsync(Long conversationId) {
        Thread.ofVirtual().name("ai-title").start(() -> summarizeTitle(conversationId));
    }

    /**
     * 用轻量模型判断会话标题是否需要更新（≤ titleMaxLength 字），失败不影响主流程。
     */
    private void summarizeTitle(Long conversationId) {
        try {
            AiConversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null) {
                return;
            }
            List<AiMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversationId)
                    .orderByAsc(AiMessage::getId)
                    .last("LIMIT 8"));
            if (recent.isEmpty()) {
                return;
            }
            StringBuilder dialogue = new StringBuilder();
            for (AiMessage m : recent) {
                dialogue.append(m.getRole()).append(": ").append(truncate(m.getContent(), 200)).append("\n");
            }
            String currentTitle = conversation.getTitle() == null || conversation.getTitle().isBlank()
                    ? "（暂无）" : conversation.getTitle();
            String instruction = prompts.getSummarizeTitle()
                    .replace("{currentTitle}", currentTitle)
                    .replace("{dialogue}", dialogue);
            AiModelSpec spec = aiSupport.resolveLightSpec();
            AiResponse response = aiProvider.chat(spec, List.of(
                    ChatMessage.builder().role("user").content(instruction).build()));
            String result = response.getContent() == null ? "" : response.getContent().trim();
            if (result.isBlank() || "不变".equals(result)) {
                return;
            }
            result = result.replaceAll("[\"“”「」『』《》]", "").replaceAll("[。.]+$", "").trim();
            if (result.length() > properties.getTitleMaxLength()) {
                result = result.substring(0, properties.getTitleMaxLength());
            }
            if (result.isBlank()) {
                return;
            }
            AiConversation update = new AiConversation();
            update.setId(conversationId);
            update.setTitle(result);
            conversationMapper.updateById(update);
            log.info("会话 {} 标题已更新：{}", conversationId, result);
        } catch (Exception e) {
            log.warn("会话 {} 标题判断失败", conversationId, e);
        }
    }

    private AiConversation requireOwnConversation(Long userId, String conversationId) {
        AiConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getClientId, conversationId));
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        return conversation;
    }

    private List<ChatMessage> buildContext(AiConversation conversation, boolean modelSupportsVision) {
        // 已压缩部分只以摘要形式存在，只取压缩游标之后的原文
        long cursor = conversation.getSummarizedUntilId() == null ? 0L : conversation.getSummarizedUntilId();
        List<AiMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversation.getId())
                .gt(AiMessage::getId, cursor)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + properties.getChatContextMaxMessages()));
        Collections.reverse(recent);
        List<ChatMessage> out = new ArrayList<>();
        String summary = conversation.getSummary();
        String basePrompt = prompts.getChatSystem() + "\n" + prompts.getSafety();
        String systemPrompt = (summary == null || summary.isBlank())
                ? basePrompt
                : basePrompt + "\n\n【历史对话摘要】（更早的对话已压缩为摘要）\n" + summary;
        out.add(ChatMessage.builder().role("system").content(systemPrompt).build());
        for (AiMessage m : recent) {
            // 引用笔记的消息，在原文前插入一条 system 引用消息（文字快照）
            NoteSnapshot snapshot = parseSnapshot(m.getNoteSnapshot());
            // 文本模型无法处理历史图片，只传文本保证对话连贯
            List<String> images = modelSupportsVision ? parseImages(m.getImages()) : null;
            if (snapshot != null) {
                out.add(buildNoteReference(snapshot));
                // ds 要求图片只能出现在 user 消息，把笔记图片合并到该用户消息上
                if (modelSupportsVision) {
                    images = mergeImages(images, snapshot.getImages());
                }
            }
            out.add(ChatMessage.builder()
                    .role(m.getRole())
                    .content(m.getContent())
                    .imageUrls(images)
                    .build());
        }
        return out;
    }

    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new BusinessException("图片数据异常");
        }
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("消息图片 JSON 解析失败：{}", json, e);
            return null;
        }
    }

    private String toJson(NoteSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BusinessException("笔记数据异常");
        }
    }

    private NoteSnapshot parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, NoteSnapshot.class);
        } catch (Exception e) {
            log.warn("笔记快照 JSON 解析失败：{}", json, e);
            return null;
        }
    }

    /** 组装引用笔记的 system 消息（仅文字快照；笔记图片由调用方合并到用户消息上） */
    private ChatMessage buildNoteReference(NoteSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        String title = snapshot.getTitle() == null || snapshot.getTitle().isBlank() ? "无标题" : snapshot.getTitle();
        sb.append("用户在提问时引用了笔记《").append(title).append("》")
                .append("（点赞 ").append(nz(snapshot.getLikeCount()))
                .append("，收藏 ").append(nz(snapshot.getFavCount()))
                .append("，浏览 ").append(nz(snapshot.getViewCount()))
                .append("，评论 ").append(nz(snapshot.getCommentCount())).append("）");
        if (snapshot.getContent() != null && !snapshot.getContent().isBlank()) {
            sb.append("\n笔记正文：\n").append(snapshot.getContent());
        }
        if (snapshot.getComments() != null && !snapshot.getComments().isEmpty()) {
            sb.append("\n评论区精选：\n");
            int i = 1;
            for (String c : snapshot.getComments()) {
                sb.append(i++).append(". ").append(c).append("\n");
            }
        }
        sb.append("\n请结合以上笔记信息回答用户的问题，信息不足时如实说明。");
        return ChatMessage.builder()
                .role("system")
                .content(sb.toString())
                .build();
    }

    /** 合并两组图片 URL，并截断到配置的最大张数 */
    private List<String> mergeImages(List<String> base, List<String> extra) {
        List<String> merged = new ArrayList<>();
        if (base != null) {
            merged.addAll(base);
        }
        if (extra != null) {
            merged.addAll(extra);
        }
        int max = properties.getMaxImages();
        if (merged.size() > max) {
            merged = merged.subList(0, max);
        }
        return merged.isEmpty() ? null : merged;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
