package com.lzz.lime_server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 大模型配置
 * （application.yaml 的 ai.* 节点，.env 环境变量覆盖）
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** API 服务地址（OpenAI 兼容协议），不带末尾斜杠 */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key，在 .env 的 AI_API_KEY 配置 */
    private String apiKey = "";

    /** 默认文本模型 */
    private String model = "deepseek-v4-flash";

    /** 默认视觉（识图）模型 */
    private String visionModel = "deepseek-v4-flash-vision-exp";

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 5;

    /** 单次请求总超时（秒），SSE 流式按此时间掐断 */
    private int readTimeoutSeconds = 90;

    /** 每用户每分钟调用次数（写作/聊天各自独立计数） */
    private int rateLimitPerMinute = 5;

    /** 每用户每天调用次数 */
    private int rateLimitPerDay = 50;

    /** 翻译：每用户每分钟调用次数 */
    private int translateRateLimitPerMinute = 10;

    /** 翻译：每用户每天调用次数 */
    private int translateRateLimitPerDay = 100;

    /** 聊天上下文携带的最大历史消息条数 */
    private int chatContextMaxMessages = 20;

    /** 未压缩消息数超过阈值时触发滚动摘要压缩 */
    private int summarizeTrigger = 30;

    /** 每次压缩的消息条数（压缩后至少保留 chatContextMaxMessages 条原文） */
    private int summarizeBatch = 10;

    /** AI 总结会话标题的最大字数 */
    private int titleMaxLength = 15;

    /** 单次输入最大字符数 */
    private int maxMessageLength = 2000;

    /** 写作辅助/聊天单次携带的最大图片数 */
    private int maxImages = 4;

    /** 是否把图片转为 base64 内联传给模型 */
    private boolean imagesAsBase64 = false;

    /** 轻量任务（标题总结/摘要/翻译）API 地址，留空则复用主模型 */
    private String lightBaseUrl = "";

    /** 轻量任务 API Key */
    private String lightApiKey = "";

    /** 轻量任务模型名 */
    private String lightModel = "glm-4.7-flash";

    /** Dots 模型 API 地址 */
    private String dotsBaseUrl = "";

    /** Dots 模型 API Key */
    private String dotsApiKey = "";

    /** Dots 是否启用深度思考 */
    private boolean dotsEnableThinking = false;

    /** 翻译默认模型*/
    private String translateModel = "dots3-note-prev";

    /** Tavily 联网搜索 API Key */
    private String tavilyApiKey = "";
}
