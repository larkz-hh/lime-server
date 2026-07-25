package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NoteDetailResponse {

    private Long id;
    private String title;
    private String content;
    private Integer status;
    private List<ImageItem> images;
    private Integer likeCount;
    private Integer favCount;
    private Integer viewCount;
    private Boolean liked;
    private Boolean favorited;
    private AuthorInfo author;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Data
    public static class ImageItem {
        private Long id;
        private String url;
        private Integer sortOrder;
    }

    @Data
    public static class AuthorInfo {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
