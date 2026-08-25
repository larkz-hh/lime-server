package com.lzz.lime_server.ai;

import lombok.Builder;
import lombok.Data;

/**
 * 非流式调用结果
 */
@Data
@Builder
public class AiResponse {

    /** 模型回复全文 */
    private String content;

    /** 输入 token 数（-1 服务端未返回） */
    private long promptTokens;

    /** 输出 token 数（-1 服务端未返回） */
    private long completionTokens;
}
