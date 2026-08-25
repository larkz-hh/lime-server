package com.lzz.lime_server.ai;

import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.config.AiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * 图片地址解析
 * 生产环境（ai.images-as-base64=false）使用公网 URL，DeepSeek 自行下载；
 * 本地开发（ai.images-as-base64=true）时从内网 MinIO 下载图片并转为 base64 data URL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImageResolver {

    /** 单张图片最大下载大小（10MB），超出拒绝 */
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final AiProperties properties;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.public-endpoint}")
    private String minioPublicEndpoint;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** 将图片地址解析为可放进 image_url.url 的值（公网 URL 或 base64 data URL） */
    public String resolve(String url) {
        if (!properties.isImagesAsBase64()) {
            return url; // 生产：直接传公网 URL
        }
        if (url == null || url.startsWith("data:")) {
            return url; // 已是 base64
        }
        try {
            String downloadUrl = toInternalUrl(url);
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(downloadUrl))
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BusinessException("图片下载失败（HTTP " + response.statusCode() + "），请检查图片地址");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new BusinessException("图片下载失败：内容为空");
            }
            if (body.length > MAX_IMAGE_BYTES) {
                throw new BusinessException("图片过大，无法处理");
            }
            String mime = response.headers().firstValue("Content-Type")
                    .filter(v -> v != null && !v.isBlank())
                    .orElse(guessMime(downloadUrl));
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(body);
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("图片下载被中断");
        } catch (Exception e) {
            log.warn("图片转 base64 失败：{}", url, e);
            throw new BusinessException("图片下载失败，请检查图片地址是否可访问");
        }
    }

    /** 把客户端传来的公网地址换成容器内网地址（如 lime-minio:9000），便于本地下载 */
    private String toInternalUrl(String url) {
        String publicEndpoint = trimTrailingSlash(minioPublicEndpoint);
        if (publicEndpoint != null && !publicEndpoint.isBlank() && url.startsWith(publicEndpoint)) {
            return trimTrailingSlash(minioEndpoint) + url.substring(publicEndpoint.length());
        }
        return url;
    }

    private String guessMime(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) {
            return "image/png";
        }
        if (lower.contains(".gif")) {
            return "image/gif";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String trimTrailingSlash(String s) {
        return s == null ? null : s.replaceAll("/+$", "");
    }
}
