package com.lzz.lime_server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * AI 业务公共支持：请求模型解析 + SSE 流式发射。
 * SSE 事件 data 均为 JSON 字符串：
 * <pre>
 * {"type":"delta","content":"增量文本"}
 * {"type":"done", ...}   由 doneEventBuilder 生成
 * {"type":"error","message":"错误提示"}
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSupport {

    private final AiProperties properties;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;

    /**
     * 解析请求指定的模型，返回可调用的 AiModelSpec。
     * <p>model 为空时按场景选默认模型；needVision=true 时必须用支持视觉的模型——
     * 用户显式选了文本模型时自动升级为视觉模型。</p>
     */
    public AiModelSpec resolveSpec(String requestedModel, boolean needVision) {
        if (requestedModel == null || requestedModel.isBlank()) {
            String model = needVision ? properties.getVisionModel() : properties.getModel();
            AiModelInfo info = AiModelCatalog.find(model).orElse(null);
            if (needVision && (info == null || !info.isSupportsVision())) {
                throw new BusinessException("视觉模型未在支持列表内，请检查 AI_VISION_MODEL 配置");
            }
            return buildSpec(model, info != null && info.isSupportsVision());
        }
        AiModelInfo info = AiModelCatalog.find(requestedModel)
                .orElseThrow(() -> new BusinessException("不支持的模型：" + requestedModel));
        if (needVision && !info.isSupportsVision()) {
            // 带图场景自动升级为视觉模型
            AiModelInfo vision = AiModelCatalog.find(properties.getVisionModel()).orElse(null);
            if (vision == null || !vision.isSupportsVision()) {
                throw new BusinessException("视觉模型未在支持列表内，请检查 AI_VISION_MODEL 配置");
            }
            return buildSpec(vision.getName(), true);
        }
        return buildSpec(info.getName(), info.isSupportsVision());
    }

    private AiModelSpec buildSpec(String model, boolean supportsVision) {
        AiModelSpec.AiModelSpecBuilder builder = AiModelSpec.builder()
                .model(model)
                .supportsVision(supportsVision);
        if (isDotsModel(model)) {
            builder.baseUrl(properties.getDotsBaseUrl())
                    .apiKey(properties.getDotsApiKey())
                    .authType("api-key")
                    .pathPrefix("/v1")
                    .enableThinking(properties.isDotsEnableThinking());
        } else if (isKimiModel(model)) {
            builder.baseUrl(properties.getKimiBaseUrl())
                    .apiKey(properties.getKimiApiKey())
                    .authType("bearer")
                    .pathPrefix("/v1")
                    .extraBody(Map.of("thinking", Map.of("type",
                            properties.isKimiEnableThinking() ? "enabled" : "disabled")));
        } else {
            builder.baseUrl(properties.getBaseUrl())
                    .apiKey(properties.getApiKey())
                    .authType("bearer")
                    .pathPrefix("");
        }
        return builder.build();
    }

    /** 判断是否 Dots 模型（走独立地址与 api-key 认证） */
    private boolean isDotsModel(String model) {
        return model != null && model.startsWith("dots");
    }

    /** 判断是否 Kimi 模型（走独立地址与 Bearer 认证） */
    private boolean isKimiModel(String model) {
        return model != null && model.startsWith("kimi");
    }

    /**
     * 轻量任务专用模型；未配置 light 模型时回退到主文本模型。
     */
    public AiModelSpec resolveLightSpec() {
        if (properties.getLightBaseUrl() == null || properties.getLightBaseUrl().isBlank()) {
            return buildSpec(properties.getModel(), false);
        }
        return AiModelSpec.builder()
                .baseUrl(properties.getLightBaseUrl())
                .apiKey(properties.getLightApiKey())
                .model(properties.getLightModel())
                .supportsVision(false)
                .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                .build();
    }

    /**
     * 启动一次 SSE 流式调用并立即返回 SseEmitter，实际请求在虚拟线程中执行。
     * <p>doneEventBuilder 在流正常结束、拿到全文后回调（用于落库等），
     * 返回 done 事件的 data 字符串；返回 null 则不发送 done 事件直接结束。</p>
     */
    public SseEmitter startStream(AiModelSpec spec, List<ChatMessage> messages,
                                  Function<String, String> doneEventBuilder) {
        return startStream(spec, messages, doneEventBuilder, null, null);
    }

    public SseEmitter startStream(AiModelSpec spec, List<ChatMessage> messages,
                                  Function<String, String> doneEventBuilder,
                                  Consumer<String> onDelta, Consumer<String> onError) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException("AI 服务未配置 API Key，请联系管理员");
        }
        SseEmitter emitter = new SseEmitter((properties.getReadTimeoutSeconds() + 15) * 1000L);
        StringBuilder full = new StringBuilder();
        Thread.ofVirtual().name("ai-stream").start(() -> {
            try {
                aiProvider.streamChat(spec, messages, new AiStreamCallback() {
                    @Override
                    public void onDelta(String text) {
                        full.append(text);
                        try {
                            emitter.send(SseEmitter.event().data(deltaEvent(text)));
                        } catch (Exception e) {
                            log.debug("SSE 发送失败（客户端可能已断开）：{}", e.getMessage());
                        }
                        if (onDelta != null) {
                            try {
                                onDelta.accept(text);
                            } catch (Exception e) {
                                log.warn("AI 增量持久化失败：{}", e.getMessage());
                            }
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
                            safeSendError(emitter, "AI 处理失败，请稍后重试");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        fireError(onError, message);
                        safeSendError(emitter, message);
                    }
                });
            } catch (BusinessException e) {
                fireError(onError, e.getMessage());
                safeSendError(emitter, e.getMessage());
            } catch (Exception e) {
                log.error("AI 流式调用异常", e);
                fireError(onError, "AI 服务暂时不可用，请稍后重试");
                safeSendError(emitter, "AI 服务暂时不可用，请稍后重试");
            }
        });
        return emitter;
    }

    private void fireError(Consumer<String> onError, String message) {
        if (onError == null) {
            return;
        }
        try {
            onError.accept(message);
        } catch (Exception e) {
            log.warn("AI 错误持久化失败：{}", e.getMessage());
        }
    }

    private void safeSendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(errorEvent(message)));
        } catch (Exception ignored) {
            // 客户端已断开
        }
        emitter.complete();
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
}
