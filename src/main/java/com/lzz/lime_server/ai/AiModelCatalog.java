package com.lzz.lime_server.ai;

import java.util.List;
import java.util.Optional;

/**
 * 平台内置模型目录。
 * 模型名全部走配置。
 */
public final class AiModelCatalog {

    public static final List<AiModelInfo> MODELS = List.of(
            new AiModelInfo("deepseek-v4-flash", "DeepSeek V4 Flash", "响应快，适合日常写作与聊天", false),
            new AiModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", "深度推理，长文写作质量更高", false),
            new AiModelInfo("deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash Vision", "支持图片理解（识图、看图写文案）", true)
    );

    private AiModelCatalog() {
    }

    public static List<AiModelInfo> list() {
        return MODELS;
    }

    public static Optional<AiModelInfo> find(String name) {
        return MODELS.stream().filter(m -> m.getName().equals(name)).findFirst();
    }
}
