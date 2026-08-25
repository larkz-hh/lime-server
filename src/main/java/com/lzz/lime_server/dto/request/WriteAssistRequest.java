package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WriteAssistRequest {

    /** 待处理文字（看图写文案 caption 时可为空） */
    @Size(max = 2000, message = "内容不能超过 2000 字")
    private String content;

    /** 操作类型：polish=润色 continue=续写 title=起标题 condense=精简 caption=看图写文案 */
    @NotBlank(message = "action 不能为空")
    private String action;

    /** 图片 URL 列表（看图写文案，最多 4 张） */
    @Size(max = 4, message = "最多携带 4 张图片")
    private List<String> imageUrls;

    /** 指定模型（可选） */
    private String model;
}
