package com.lzz.lime_server.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型请求的工具调用
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiToolCall {

    /** 工具调用 id */
    private String id;

    /** 工具名 */
    private String name;

    /** 工具参数*/
    private String arguments;
}
