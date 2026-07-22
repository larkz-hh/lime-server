package com.lzz.lime_server.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PublishNoteRequest {

    //  0=草稿，1=已发布，默认发布
    private Integer status = 1;

    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @Size(max = 1000, message = "正文不能超过 1000 个字符")
    private String content;

    @NotEmpty(message = "至少上传一张图片")
    @Size(max = 9, message = "最多上传 9 张图片")
    @Valid
    private List<NoteImageItem> images;

    @Data
    public static class NoteImageItem {

        @NotBlank(message = "图片 URL 不能为空")
        private String url;

        private int sortOrder;
    }
}
