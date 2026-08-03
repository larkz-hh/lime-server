package com.lzz.lime_server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一级评论响应
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {

    private Long id;

    private AuthorInfo author;

    // 文字内容，纯图片/纯语音评论时为 null
    private String content;

    // 图片 URL 列表
    private List<String> images;

    // 语音 URL
    private String voiceUrl;

    // 语音时长（秒）
    private Integer voiceDuration;

    private Integer likeCount;

    private Integer replyCount;

    // 当前登录用户是否已点赞该评论
    private boolean liked;

    // 评论者是否为笔记作者本人
    private boolean isNoteAuthor;

    private LocalDateTime createTime;

    // 前 3 条回复预览，展开全部时走专门接口
    private List<ReplyResponse> topReplies;

    @Data
    public static class AuthorInfo {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
