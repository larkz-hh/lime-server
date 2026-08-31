package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatCancelRequest {

    /** 客户端生成的消息幂等键（UUID） */
    @NotBlank(message = "消息幂等键不能为空")
    private String messageClientId;

    /** 打断时客户端的半截文本 */
    private String partialContent;
}
