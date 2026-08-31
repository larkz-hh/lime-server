package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TranslateRequest {

    /** 待翻译文本 */
    @NotBlank(message = "text 不能为空")
    @Size(max = 1000, message = "待翻译文本不能超过 1000 字")
    private String text;

    /** 目标语言（如：中文、英语、日语、韩语、法语等） */
    @NotBlank(message = "targetLang 不能为空")
    private String targetLang;

    /** 源语言（可选，不传则自动检测） */
    private String sourceLang;

    /** 指定模型（可选） */
    private String model;
}
