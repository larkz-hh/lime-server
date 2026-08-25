package com.lzz.lime_server.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 内置模型目录项（ GET /api/ai/models 的响应结构）
 */
@Data
@AllArgsConstructor
public class AiModelInfo {

    /** 模型名（请求时通过 model 字段指定） */
    private String name;

    /** 展示名 */
    private String displayName;

    /** 一句话说明 */
    private String description;

    /** 是否支持图片输入（识图场景必须选 true 的模型） */
    private boolean supportsVision;
}
