package com.lzz.lime_server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 提示词配置
 * 绑定 src/main/resources/prompts.yaml 的 ai-prompts.* 节点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-prompts")
public class AiPrompts {

    /** AI 聊天系统提示（角色 + 回答要求） */
    private String chatSystem;

    /** 写作辅助：润色 */
    private String writePolish;

    /** 写作辅助：续写 */
    private String writeContinue;

    /** 写作辅助：起标题 */
    private String writeTitle;

    /** 写作辅助：精简 */
    private String writeCondense;

    /** 写作辅助：看图写文案 */
    private String writeCaption;

    /** 摘要压缩：首次生成，占位符 {dialogue} */
    private String summarizeFirst;

    /** 摘要压缩：滚动更新，占位符 {existingSummary}、{dialogue} */
    private String summarizeUpdate;

    /** 翻译，占位符 {targetLang}、{sourceLangHint} */
    private String translate;
}
