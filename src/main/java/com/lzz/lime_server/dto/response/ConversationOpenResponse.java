package com.lzz.lime_server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationOpenResponse {

    /** 格式：c2c_<IM UserID> */
    private String conversationId;
}
