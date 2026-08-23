package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DanmakuResponse {

    private Long id;
    private String content;
    private Long videoTimeMs;
    private String color;
    private AuthorBrief author;
    private LocalDateTime createTime;

    @Data
    public static class AuthorBrief {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
