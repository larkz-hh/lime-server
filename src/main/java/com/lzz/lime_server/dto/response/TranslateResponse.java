package com.lzz.lime_server.dto.response;

import lombok.Data;

@Data
public class TranslateResponse {

    /** 译文 */
    private String translatedText;

    /** 源语言（请求未指定时为自动检测结果，指定时原样返回） */
    private String sourceLang;

    /** 目标语言 */
    private String targetLang;
}
