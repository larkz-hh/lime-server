package com.lzz.lime_server.service;

import com.lzz.lime_server.ai.AiModelInfo;
import com.lzz.lime_server.dto.request.TranslateRequest;
import com.lzz.lime_server.dto.request.WriteAssistRequest;
import com.lzz.lime_server.dto.response.TranslateResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiService {

    /** 内置模型列表 */
    List<AiModelInfo> getModels();

    /** 写作辅助 / 看图写文案（SSE 流式） */
    SseEmitter assist(Long userId, WriteAssistRequest request);

    /** AI 翻译（非流式，短文本一次返回） */
    TranslateResponse translate(Long userId, TranslateRequest request);
}
