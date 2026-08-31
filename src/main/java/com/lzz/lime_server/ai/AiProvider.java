package com.lzz.lime_server.ai;

import java.util.List;

/**
 * AI 模型调用抽象，屏蔽不同服务商差异（统一走 OpenAI 兼容协议）。
 */
public interface AiProvider {

    /**
     * 非流式调用，阻塞直到拿到完整回复。
     */
    AiResponse chat(AiModelSpec spec, List<ChatMessage> messages);

    /**
     * SSE 流式调用，阻塞直到流结束；期间通过 callback 回调增量文本。
     * 调用方需自行在独立线程（虚拟线程）中执行，避免阻塞请求线程。
     */
    void streamChat(AiModelSpec spec, List<ChatMessage> messages, AiStreamCallback callback);
}
