package com.lzz.lime_server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 二级回复响应
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplyResponse {

    private Long id;

    private AuthorInfo author;

    // 被回复的用户 ID（用于 App 跳转）
    private Long replyToUserId;

    // 被回复用户的昵称（用于显示"回复@xxx"）
    private String replyToNickname;

    // 文字内容（含 emoji）
    private String content;

    // 图片 URL 列表
    private List<String> images;

    // 语音 URL
    private String voiceUrl;

    // 语音时长（秒）
    private Integer voiceDuration;

    private Integer likeCount;

    // 当前登录用户是否已点赞该回复
    private boolean liked;

    // 回复者是否为笔记作者本人
    @JsonProperty("isNoteAuthor")
    private boolean isNoteAuthor;

    private LocalDateTime createTime;

    // IP 属地，为 null 时不输出
    private String ipLocation;

    @Data
    public static class AuthorInfo {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
