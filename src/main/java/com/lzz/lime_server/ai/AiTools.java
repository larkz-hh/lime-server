package com.lzz.lime_server.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 工具声明集中管理
 */
public final class AiTools {

    /** 天气工具（始终挂载，免费） */
    private static final Map<String, Object> WEATHER_TOOL = Map.of(
            "type", "function",
            "function", Map.of(
                    "name", "get_weather",
                    "description", "查询指定地点的实时天气。用户询问天气、气温、下雨、湿度等情况时调用。",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "location", Map.of("type", "string", "description", "城市或地点名称，如 北京、上海、杭州")
                            ),
                            "required", List.of("location")
                    )
            )
    );

    /** 联网搜索工具，默认开启 */
    private static final Map<String, Object> SEARCH_TOOL = Map.of(
            "type", "function",
            "function", Map.of(
                    "name", "search_web",
                    "description", "联网搜索最新/实时信息。仅当用户询问需要实时数据的问题（新闻、价格、攻略、时事、最新进展）时才调用；常识、写作、聊天无需调用。",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string", "description", "要搜索的问题或关键词")
                            ),
                            "required", List.of("query")
                    )
            )
    );

    private AiTools() {
    }

    /**
     * 组装聊天工具列表
     */
    public static List<Map<String, Object>> chatTools(boolean enableSearch) {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(WEATHER_TOOL);
        if (enableSearch) {
            tools.add(SEARCH_TOOL);
        }
        return tools;
    }
}
