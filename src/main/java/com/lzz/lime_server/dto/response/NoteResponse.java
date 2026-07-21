package com.lzz.lime_server.dto.response;

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
    private List<ImageItem> images;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Data
    public static class ImageItem {
        private Long id;
        private String url;
        private Integer sortOrder;
    }
}
