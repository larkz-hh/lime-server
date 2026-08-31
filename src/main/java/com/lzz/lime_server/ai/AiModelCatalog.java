package com.lzz.lime_server.ai;

import java.util.List;
import java.util.Optional;

/**
 * 平台内置模型目录。
 * 模型名全部走配置。
 */
public final class AiModelCatalog {

    private static final List<AiModelInfo> USER_MODELS = List.of(
            new AiModelInfo("deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash Vision", "支持图片理解（识图、看图写文案）", true),
            new AiModelInfo("dots3-note-prev", "Dots 3 Note", "小红书 Dots 模型，支持文字与图片理解", true),
            new AiModelInfo("kimi-k2.6", "Kimi K2.6", "Moonshot Kimi 模型，支持文字与图片理解", true)
    );

    private AiModelCatalog() {
    }

    public static List<AiModelInfo> list() {
        return USER_MODELS;
    }

    public static Optional<AiModelInfo> find(String name) {
        return USER_MODELS.stream().filter(m -> m.getName().equals(name)).findFirst();
    }
}
