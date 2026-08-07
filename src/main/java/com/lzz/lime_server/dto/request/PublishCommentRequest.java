package com.lzz.lime_server.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 发布评论/回复请求体
 * <p>
 * 内容约束：
 * - content、images、voiceUrl 至少提供一项
 * - images 与 voiceUrl 互斥，不可同时存在
 * - images 最多 9 张
 * - emoji 属于文字内容，content 字段存储
 * </p>
 */
@Data
public class PublishCommentRequest {

    // 文字内容（可含 emoji），可为空
    private String content;

    // 图片 URL 列表（先调上传接口获得 URL），最多 9 张；与 voiceUrl 互斥
    private List<String> images;

    // 语音文件 URL（先调上传接口获得 URL）；与 images 互斥
    private String voiceUrl;

    // 语音时长（秒），由客户端录音后上传，与 voiceUrl 同时传
    private Integer voiceDuration;

    // 回复目标用户 ID（仅二级回复时传）
    private Long replyToUserId;
}
