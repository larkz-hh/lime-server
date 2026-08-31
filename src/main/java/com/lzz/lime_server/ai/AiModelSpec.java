package com.lzz.lime_server.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 一次 AI 调用所需的模型规格。
 * 内置模型由系统配置组装；「用户自定义模型」由数据库记录组装。
 */
@Data
@Builder
public class AiModelSpec {

    /** 服务地址（OpenAI 兼容），不带末尾斜杠 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名 */
    private String model;

    /** 是否支持图片输入 */
    private boolean supportsVision;

    /** 认证方式：bearer（默认，Authorization: Bearer xxx*/
    @Builder.Default
    private String authType = "bearer";

    /** 端点路径前缀,默认空字符串 */
    @Builder.Default
    private String pathPrefix = "";

    /** 深度思考开关：null 不传用服务默认*/
    private Boolean enableThinking;

    /** 额外请求体参数（thinking 开关等），可空 */
    private Map<String, Object> extraBody;

    /** 工具声明列表,可空 */
    private List<Map<String, Object>> tools;
}
