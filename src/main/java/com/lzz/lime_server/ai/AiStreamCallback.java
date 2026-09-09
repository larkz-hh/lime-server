package com.lzz.lime_server.ai;

import java.util.List;

/**
 * SSE 流式调用回调
 */
public interface AiStreamCallback {

    /** 收到一段增量文本 */
    void onDelta(String text);

    /** 流式响应中识别出的工具调用：在 onComplete 之前回调，无工具时列表为空 */
    default void onToolCalls(List<AiToolCall> toolCalls) {
    }

    /** 流正常结束（调用方自行拼接） */
    void onComplete();

    /** 出错，message 为可展示的中文提示 */
    void onError(String message);
}
