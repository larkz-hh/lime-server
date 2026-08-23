package com.lzz.lime_server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NoteResponse {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private Integer status;
    // 笔记类型：1=图文, 2=视频
    private Integer noteType;
    private List<ImageItem> images;
    // 视频信息，图文笔记为 null 不输出
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private NoteVideoInfo video;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Data
    public static class ImageItem {
        private Long id;
        private String url;
        // 图片宽高（客户端上报，可能为 null 不输出）
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer width;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer height;
        private Integer sortOrder;
    }
}
