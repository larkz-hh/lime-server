package com.lzz.lime_server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 笔记卡片 / 详情中嵌套的视频信息
 * <p>仅视频笔记（noteType=2）有值，图文笔记为 null 不输出。
 * orientation 由视频宽高派生：width &gt; height 为 LANDSCAPE，否则 PORTRAIT，
 * 客户端据此决定横屏/竖屏播放。</p>
 */
@Data
public class NoteVideoInfo {

    private Long durationMs;

    private Integer width;

    private Integer height;

    /** 横竖屏：LANDSCAPE / PORTRAIT */
    private String orientation;

    /** 播放地址（直放模式为原片；未来转码模式切 hls，字段名不变） */
    private String playUrl;

    /** 封面地址，可为 null */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String coverUrl;

    /**
     * 由视频元数据组装视频信息对象。
     *
     * @param durationMs 时长（毫秒）
     * @param width      视频宽
     * @param height     视频高
     * @param playUrl    播放地址
     * @param coverUrl   封面地址，可为 null
     */
    public static NoteVideoInfo of(Long durationMs, Integer width, Integer height, String playUrl, String coverUrl) {
        NoteVideoInfo info = new NoteVideoInfo();
        info.setDurationMs(durationMs);
        info.setWidth(width);
        info.setHeight(height);
        info.setOrientation(width != null && height != null && width > height ? "LANDSCAPE" : "PORTRAIT");
        info.setPlayUrl(playUrl);
        info.setCoverUrl(coverUrl);
        return info;
    }
}
