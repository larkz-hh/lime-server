package com.lzz.lime_server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lzz.lime_server.ai.*;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.config.AiPrompts;
import com.lzz.lime_server.config.AiProperties;
import com.lzz.lime_server.dto.request.TranslateRequest;
import com.lzz.lime_server.dto.request.WriteAssistRequest;
import com.lzz.lime_server.dto.response.TranslateResponse;
import com.lzz.lime_server.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final Set<String> ACTIONS = Set.of("polish", "continue", "title", "condense", "caption");

    /** polish/continue/condense 要求的最短内容长度（字符） */
    private static final int MIN_CONTENT_CHARS = 5;

    /** title 要求的最短内容长度 */
    private static final int MIN_TITLE_CHARS = 2;

    private final AiProperties properties;
    private final AiPrompts prompts;
    private final AiSupport aiSupport;
    private final AiProvider aiProvider;
    private final AiRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiModelInfo> getModels() {
        return AiModelCatalog.list();
    }

    @Override
    public SseEmitter assist(Long userId, WriteAssistRequest request) {
        String action = request.getAction();
        if (!ACTIONS.contains(action)) {
            throw new BusinessException("action 非法，可选值：" + String.join(" / ", ACTIONS));
        }
        List<String> images = request.getImageUrls();
        boolean hasImages = images != null && !images.isEmpty();
        boolean hasContent = request.getContent() != null && !request.getContent().isBlank();
        if (!hasContent && !hasImages) {
            throw new BusinessException("content 与 imageUrls 至少提供一项");
        }
        if ("caption".equals(action) && !hasImages) {
            throw new BusinessException("看图写文案（caption）必须携带 imageUrls");
        }
        if (hasContent && !"caption".equals(action)) {
            int min = MIN_CONTENT_CHARS;
            String message = "内容太短了，先写几句话再让 AI 优化吧";
            if ("title".equals(action)) {
                if (hasImages) {
                    min = 0; // 有图：标题可看图生成，文字任意
                } else {
                    min = MIN_TITLE_CHARS;
                    message = "内容太短了，先写几个词再起标题吧";
                }
            }
            if (request.getContent().trim().length() < min) {
                throw new BusinessException(message);
            }
        }

        rateLimiter.check(userId, "write", properties.getRateLimitPerMinute(), properties.getRateLimitPerDay());

        AiModelSpec spec = aiSupport.resolveSpec(request.getModel(), hasImages);
        ChatMessage prompt = buildPrompt(action, request.getContent(), images);
        return aiSupport.startStream(spec, List.of(prompt), fullText -> {
            ObjectNode done = objectMapper.createObjectNode();
            done.put("type", "done");
            done.put("content", stripDataTags(fullText));
            done.put("model", spec.getModel());
            return done.toString();
        });
    }

    @Override
    public TranslateResponse translate(Long userId, TranslateRequest request) {
        rateLimiter.check(userId, "translate",
                properties.getTranslateRateLimitPerMinute(), properties.getTranslateRateLimitPerDay());

        // 翻译默认走配置翻译模型
        AiModelSpec spec;
        if (request.getModel() != null && !request.getModel().isBlank()) {
            spec = aiSupport.resolveSpec(request.getModel(), false);
        } else if (properties.getTranslateModel() != null && !properties.getTranslateModel().isBlank()) {
            spec = aiSupport.resolveSpec(properties.getTranslateModel(), false);
        } else {
            spec = aiSupport.resolveLightSpec();
        }

        String sourceLangHint = (request.getSourceLang() == null || request.getSourceLang().isBlank())
                ? "源语言请自动检测。"
                : "源语言是" + request.getSourceLang() + "。";
        String instruction = prompts.getTranslate()
                .replace("{targetLang}", request.getTargetLang())
                .replace("{sourceLangHint}", sourceLangHint);
        String text = instruction + "\n\n<data>\n" + request.getText() + "\n</data>";

        AiResponse response = aiProvider.chat(spec, List.of(
                ChatMessage.builder().role("user").content(text).build()));

        TranslateResponse out = new TranslateResponse();
        out.setTranslatedText(stripDataTags(response.getContent()));
        out.setTargetLang(request.getTargetLang());
        out.setSourceLang(request.getSourceLang());
        return out;
    }

    /**
     * 组装提示词：系统指令固定，用户内容放入 data 标签作为纯数据处理，防提示词注入。
     */
    private ChatMessage buildPrompt(String action, String content, List<String> images) {
        String instruction = switch (action) {
            case "polish" -> prompts.getWritePolish();
            case "continue" -> prompts.getWriteContinue();
            case "title" -> prompts.getWriteTitle();
            case "condense" -> prompts.getWriteCondense();
            case "caption" -> prompts.getWriteCaption();
            default -> throw new BusinessException("action 非法");
        };
        String safe = content == null ? "" : content;
        String text = instruction + "\n" + prompts.getSafety() + "\n\n<data>\n" + safe + "\n</data>";
        return ChatMessage.builder().role("user").content(text).imageUrls(images).build();
    }

    /// 剥掉<data> 标签
    private String stripDataTags(String text) {
        if (text == null) return null;
        return text.replace("<data>", "").replace("</data>", "").trim();
    }
}
