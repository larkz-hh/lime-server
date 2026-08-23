package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostDanmakuRequest {

    @NotBlank(message = "弹幕内容不能为空")
    @Size(max = 200, message = "弹幕不能超过 200 个字符")
    private String content;

    /** 弹幕出现时间点（毫秒，相对视频开头），由客户端按当前播放位置传入 */
    @NotNull(message = "弹幕时间点不能为空")
    @Min(value = 0, message = "弹幕时间点不能为负")
    private Long videoTimeMs;

    /** 弹幕颜色（#RRGGBB），可选；null 时客户端用默认白色 */
    private String color;
}
