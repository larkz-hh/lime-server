package com.lzz.lime_server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.config.AiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议的模型调用实现。
 * 请求体按 {@code POST {baseUrl}/chat/completions} 组装，支持文本与 image_url 图片消息、
 * 非流式与 SSE 流式（data: 行）两种返回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleProvider implements AiProvider {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final AiImageResolver imageResolver;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public AiResponse chat(AiModelSpec spec, List<ChatMessage> messages) {
        HttpRequest request = buildRequest(spec, messages, false);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseNonStreamResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("AI 请求被中断，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 非流式调用失败", e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public void streamChat(AiModelSpec spec, List<ChatMessage> messages, AiStreamCallback callback) {
        HttpRequest request = buildRequest(spec, messages, true);
        try {
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                String body = response.body() == null ? "" : String.join("\n", response.body().toList());
                callback.onError(extractErrorMessage(body));
                return;
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> handleStreamLine(line, callback));
            }
            callback.onComplete();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.onError("AI 请求被中断，请稍后重试");
        } catch (Exception e) {
            log.error("AI 流式调用失败", e);
            callback.onError("AI 服务暂时不可用，请稍后重试");
        }
    }

    // ===== 请求组装 =====

    private HttpRequest buildRequest(AiModelSpec spec, List<ChatMessage> messages, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", spec.getModel());
        body.put("stream", stream);
        ArrayNode msgs = body.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode msg = msgs.addObject();
            msg.put("role", m.getRole());
            if ("tool".equals(m.getRole())) {
                // 工具结果消息
                msg.put("content", m.getContent() == null ? "" : m.getContent());
                if (m.getToolCallId() != null) {
                    msg.put("tool_call_id", m.getToolCallId());
                }
                continue;
            }
            if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                // assistant 回传工具调用
                msg.put("content", m.getContent() == null ? "" : m.getContent());
                ArrayNode tcs = msg.putArray("tool_calls");
                for (AiToolCall tc : m.getToolCalls()) {
                    ObjectNode tcNode = tcs.addObject();
                    tcNode.put("id", tc.getId());
                    tcNode.put("type", "function");
                    ObjectNode fn = tcNode.putObject("function");
                    fn.put("name", tc.getName());
                    fn.put("arguments", tc.getArguments());
                }
                continue;
            }
            List<String> images = m.getImageUrls();
            if (images == null || images.isEmpty()) {
                msg.put("content", m.getContent() == null ? "" : m.getContent());
            } else {
                ArrayNode content = msg.putArray("content");
                if (m.getContent() != null && !m.getContent().isEmpty()) {
                    ObjectNode textPart = content.addObject();
                    textPart.put("type", "text");
                    textPart.put("text", m.getContent());
                }
                for (String url : images) {
                    ObjectNode imagePart = content.addObject();
                    imagePart.put("type", "image_url");
                    ObjectNode imageUrl = imagePart.putObject("image_url");
                    imageUrl.put("url", imageResolver.resolve(url));
                }
            }
        }
        if (spec.getEnableThinking() != null) {
            ObjectNode kwargs = body.putObject("chat_template_kwargs");
            kwargs.put("enable_thinking", spec.getEnableThinking());
        }
        Map<String, Object> extraBody = spec.getExtraBody();
        if (extraBody != null && !extraBody.isEmpty()) {
            for (Map.Entry<String, Object> entry : extraBody.entrySet()) {
                body.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
            }
        }
        if (spec.getTools() != null && !spec.getTools().isEmpty()) {
            body.set("tools", objectMapper.valueToTree(spec.getTools()));
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException("AI 请求组装失败");
        }
        String pathPrefix = spec.getPathPrefix() == null ? "" : spec.getPathPrefix();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(spec.getBaseUrl()) + pathPrefix + "/chat/completions"))
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if ("api-key".equals(spec.getAuthType())) {
            builder.header("api-key", spec.getApiKey());
        } else {
            builder.header("Authorization", "Bearer " + spec.getApiKey());
        }
        return builder.build();
    }

    // ===== 响应解析 =====

    private AiResponse parseNonStreamResponse(HttpResponse<String> response) throws Exception {
        String body = response.body() == null ? "" : response.body();
        if (response.statusCode() != 200) {
            throw new BusinessException(extractErrorMessage(body));
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode message = root.path("choices").path(0).path("message");
        String content = message.path("content").asText("");
        List<AiToolCall> toolCalls = null;
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : toolCallsNode) {
                toolCalls.add(new AiToolCall(
                        tc.path("id").asText(""),
                        tc.path("function").path("name").asText(""),
                        tc.path("function").path("arguments").asText("")
                ));
            }
        }
        JsonNode usage = root.path("usage");
        long prompt = usage.path("prompt_tokens").asLong(-1);
        long completion = usage.path("completion_tokens").asLong(-1);
        return AiResponse.builder()
                .content(content)
                .toolCalls(toolCalls)
                .promptTokens(prompt)
                .completionTokens(completion)
                .build();
    }

    private void handleStreamLine(String line, AiStreamCallback callback) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (!line.startsWith("data:")) {
            return;
        }
        String data = line.substring(5).trim();
        if ("[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                JsonNode content = delta.path("content");
                if (content.isTextual() && !content.asText().isEmpty()) {
                    callback.onDelta(content.asText());
                }
            }
        } catch (Exception e) {
            log.warn("SSE 数据解析失败：{}", data, e);
        }
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.path("error").path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return "AI 服务返回错误：" + message.asText();
            }
        } catch (Exception ignored) {
            // 非 JSON 响应体，走兜底提示
        }
        String snippet = body == null ? "" : body;
        if (snippet.length() > 100) {
            snippet = snippet.substring(0, 100) + "...";
        }
        return "AI 服务返回错误" + (snippet.isBlank() ? "" : "（" + snippet + "）");
    }

    private String trimTrailingSlash(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }
}
