package com.lzz.lime_server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationOpenRequest {

    @NotNull(message = "目标用户不能为空")
    private Long targetUserId;
}
