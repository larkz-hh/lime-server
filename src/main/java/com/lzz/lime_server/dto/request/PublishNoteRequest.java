package com.lzz.lime_server.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PublishNoteRequest {

    //  0=草稿，1=已发布，默认发布
    private Integer status = 1;

    // 笔记类型：1=图文, 2=视频，默认图文
    private Integer noteType = 1;

    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @Size(max = 1000, message = "正文不能超过 1000 个字符")
    private String content;

    // 图文笔记必填 1~9 张；视频笔记忽略
    @Size(max = 9, message = "最多上传 9 张图片")
    @Valid
    private List<NoteImageItem> images;

    // 视频笔记必填；图文笔记忽略
    @Valid
    private VideoItem video;

    @Data
    public static class NoteImageItem {

        @NotBlank(message = "图片 URL 不能为空")
        private String url;

        // 图片宽高（客户端上报，仅封面即第一张图需要填写，其余可不传）
        @Positive(message = "图片宽度必须为正数")
        private Integer width;

        @Positive(message = "图片高度必须为正数")
        private Integer height;

        private int sortOrder;
    }

    @Data
    public static class VideoItem {

        @NotBlank(message = "视频 URL 不能为空")
        private String url;

        // 以下元数据由客户端 MediaMetadataRetriever 采集
        @NotNull(message = "视频时长不能为空")
        @Positive(message = "视频时长必须为正数")
        private Long durationMs;

        @NotNull(message = "视频宽度不能为空")
        @Positive(message = "视频宽度必须为正数")
        private Integer width;

        @NotNull(message = "视频高度不能为空")
        @Positive(message = "视频高度必须为正数")
        private Integer height;

        // 封面 URL（必填：客户端上传或截帧，瀑布流卡片与详情页展示用）
        @NotBlank(message = "视频封面不能为空")
        private String coverUrl;

        // 封面图宽高（客户端读取后上报，瀑布流卡片布局用；可为 null 兼容历史客户端）
        @Positive(message = "封面宽度必须为正数")
        private Integer coverWidth;

        @Positive(message = "封面高度必须为正数")
        private Integer coverHeight;
    }
}
