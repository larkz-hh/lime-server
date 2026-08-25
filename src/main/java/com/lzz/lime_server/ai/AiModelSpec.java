package com.lzz.lime_server.ai;

import lombok.Builder;
import lombok.Data;

/**
 * 一次 AI 调用所需的模型规格。
 * 内置模型由系统配置组  装；「用户自定义模型」由数据库记录组装。
 */
@Data
@Builder
public class AiModelSpec {

    /** 服务地址（OpenAI 兼容），如 https://api.deepseek.com，不带末尾斜杠 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名 */
    private String model;

    /** 是否支持图片输入 */
    private boolean supportsVision;
}
