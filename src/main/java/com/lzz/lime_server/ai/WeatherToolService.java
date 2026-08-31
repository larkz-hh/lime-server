package com.lzz.lime_server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzz.lime_server.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 天气工具：
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherToolService {

    private static final String WTTR_URL = "https://wttr.in/";

    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 查询天气，返回结构化中文摘要/
     * location 为空时按服务器 IP 定位
     */
    public String fetchWeather(String location) {
        String city = (location == null || location.isBlank()) ? "auto" : location.trim();
        try {
            String url = WTTR_URL + URLEncoder.encode(city, StandardCharsets.UTF_8) + "?format=j1&lang=zh";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "curl")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException("天气查询失败（HTTP " + response.statusCode() + "）");
            }
            return parse(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("天气查询被中断");
        } catch (Exception e) {
            log.warn("wttr.in 查询失败：{}", location, e);
            throw new BusinessException("天气查询失败，请稍后重试");
        }
    }

    private String parse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode cc = root.path("current_condition").path(0);
        JsonNode area = root.path("nearest_area").path(0);

        StringBuilder sb = new StringBuilder();

        // 地点
        String areaName = area.path("areaName").path(0).path("value").asText("未知地点");
        String region = area.path("region").path(0).path("value").asText("");
        String country = area.path("country").path(0).path("value").asText("");
        sb.append("地点：").append(areaName);
        if (region != null && !region.isBlank() && !region.equals(areaName)) {
            sb.append("，").append(region);
        }
        if (country != null && !country.isBlank()) {
            sb.append("，").append(country);
        }

        // 当前天气
        sb.append("\n当前天气：")
                .append(cc.path("weatherDesc").path(0).path("value").asText("未知"))
                .append("，").append(cc.path("temp_C").asText("-")).append("°C");
        String feels = cc.path("feelslike_C").asText("");
        if (feels != null && !feels.isBlank()) {
            sb.append("，体感 ").append(feels).append("°C");
        }
        sb.append("，湿度 ").append(cc.path("humidity").asText("-")).append("%");
        String wind = cc.path("windspeedKmph").asText("");
        if (wind != null && !wind.isBlank()) {
            sb.append("，风 ").append(wind).append(" km/h");
        }

        // 未来预报（今天/明天/后天）
        JsonNode weatherArr = root.path("weather");
        if (weatherArr.isArray() && !weatherArr.isEmpty()) {
            sb.append("\n未来预报：");
            int days = Math.min(weatherArr.size(), 3);
            for (int i = 0; i < days; i++) {
                JsonNode w = weatherArr.get(i);
                String label = i == 0 ? "今天" : (i == 1 ? "明天" : "后天");
                sb.append("\n- ").append(label)
                        .append("（").append(w.path("date").asText("")).append("）：")
                        .append(dayDesc(w))
                        .append("，").append(w.path("mintempC").asText("-"))
                        .append("~").append(w.path("maxtempC").asText("-")).append("°C");
                int rain = maxRainChance(w);
                if (rain > 0) {
                    sb.append("，降雨概率 ").append(rain).append("%");
                }
            }
        }
        return sb.toString();
    }

    /** 当天代表性天气描述：优先 12 点，兜底取第一条 */
    private String dayDesc(JsonNode weatherDay) {
        JsonNode hourly = weatherDay.path("hourly");
        if (hourly.isArray()) {
            for (JsonNode h : hourly) {
                if ("1200".equals(h.path("time").asText())) {
                    return h.path("weatherDesc").path(0).path("value").asText("未知");
                }
            }
            if (!hourly.isEmpty()) {
                return hourly.get(0).path("weatherDesc").path(0).path("value").asText("未知");
            }
        }
        return "未知";
    }

    /** 当天最大降雨概率（%） */
    private int maxRainChance(JsonNode weatherDay) {
        int max = 0;
        for (JsonNode h : weatherDay.path("hourly")) {
            int chance = h.path("chanceofrain").asInt(0);
            if (chance > max) {
                max = chance;
            }
        }
        return max;
    }
}
