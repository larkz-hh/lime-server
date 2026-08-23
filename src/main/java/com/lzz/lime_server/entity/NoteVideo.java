package com.lzz.lime_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频笔记元数据实体
 * <p>与 note 表 1:1 关联，仅视频笔记（note_type=2）存在。
 * 直放模式下播放地址即 originalUrl，transcodeStatus 恒为 2（可播）；
 * hls 相关字段为未来转码模式预留。</p>
 */
@Data
@TableName("note_video")
public class NoteVideo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    /** 原始视频地址（直放模式即播放地址） */
    private String originalUrl;

    /** 封面地址（客户端上传或截帧），可为 null */
    private String coverUrl;

    /** 封面图宽（客户端上报），可为 null */
    private Integer coverWidth;

    /** 封面图高（客户端上报），可为 null */
    private Integer coverHeight;

    /** 视频宽（客户端上报，用于横屏判断） */
    private Integer videoWidth;

    /** 视频高 */
    private Integer videoHeight;

    /** 视频时长（毫秒，客户端上报） */
    private Long durationMs;

    /** 转码状态：直放模式恒为 2（可播）；0=待转码, 1=转码中, 3=失败（转码模式预留） */
    private Integer transcodeStatus;

    /** 多码率 HLS 地址（转码模式预留） */
    private String hlsMasterUrl;

    /** 各档位信息 JSON（转码模式预留） */
    private String hlsLevels;

    /** 失败原因（转码模式预留） */
    private String failReason;

    /** 重试次数（转码模式预留） */
    private Integer retryCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
