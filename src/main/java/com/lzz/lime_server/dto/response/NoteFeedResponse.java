package com.lzz.lime_server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class NoteFeedResponse {

    private Long id;
    private String title;
    private String coverImage;
    private Integer likeCount;
    private Boolean liked;
    // 笔记状态：0=草稿，1=已发布；仅在用户笔记列表接口中返回，Feed 接口中为 null 不输出
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer status;
    // 浏览量：仅本人查看自己的笔记列表时返回，其他场景为 null 不输出
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer viewCount;
    private AuthorBrief author;

    @Data
    public static class AuthorBrief {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
