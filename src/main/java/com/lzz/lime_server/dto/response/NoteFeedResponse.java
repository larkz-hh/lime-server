package com.lzz.lime_server.dto.response;

import lombok.Data;

@Data
public class NoteFeedResponse {

    private Long id;
    private String title;
    private String coverImage;
    private Integer likeCount;
    private AuthorBrief author;

    @Data
    public static class AuthorBrief {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
