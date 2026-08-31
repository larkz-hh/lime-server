package com.lzz.lime_server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.config.AiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 联网搜索工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TavilySearchService {

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** 执行搜索，返回结构化文本*/
    public String search(String query) {
        if (properties.getTavilyApiKey() == null || properties.getTavilyApiKey().isBlank()) {
            throw new BusinessException("联网搜索未配置 API Key");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("query", query);
            body.put("search_depth", "basic");
            body.put("max_results", 5);
            body.put("include_answer", "basic");
            body.put("language", "zh-cn");
            HttpRequest request = HttpRequest.newBuilder(URI.create(TAVILY_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getTavilyApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException("联网搜索失败（HTTP " + response.statusCode() + "）");
            }
            return parse(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("联网搜索被中断");
        } catch (Exception e) {
            log.warn("Tavily 搜索失败：{}", query, e);
            throw new BusinessException("联网搜索失败，请稍后重试");
        }
    }

    private String parse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        StringBuilder sb = new StringBuilder();
        String answer = root.path("answer").asText("");
        if (!answer.isBlank()) {
            sb.append("摘要：").append(answer).append("\n");
        }
        JsonNode results = root.path("results");
        if (results.isArray()) {
            int i = 1;
            for (JsonNode r : results) {
                sb.append("\n").append(i++).append(". ").append(r.path("title").asText(""));
                String url = r.path("url").asText("");
                if (!url.isBlank()) {
                    sb.append("\n   ").append(url);
                }
                String content = r.path("content").asText("");
                if (!content.isBlank()) {
                    sb.append("\n   ").append(truncate(content, 300));
                }
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
