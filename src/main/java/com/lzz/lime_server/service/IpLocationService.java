package com.lzz.lime_server.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * IP 属地解析服务。
 * <p>使用内存模式（将 ip2region.xdb 全量加载到内存），每次查询不产生 IO，
 * 每次查询创建新实例，共享 contentBuf </p>
 */
@Slf4j
@Service
public class IpLocationService {

    private byte[] contentBuf;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("ip2region_v4.xdb");
            contentBuf = resource.getInputStream().readAllBytes();
            log.info("ip2region 数据库加载成功，大小: {} KB", contentBuf.length / 1024);
        } catch (Exception e) {
            log.warn("ip2region.xdb 未找到，IP 属地功能不可用: {}", e.getMessage());
        }
    }

    /**
     * 将 IP 地址解析为省份或国家名，无法解析时返回 null。
     * <p>国内返回省份；境外返回国家名。</p>
     */
    public String resolve(String ip) {
        if (contentBuf == null || ip == null || ip.isBlank()) return null;
        try {
            Searcher searcher = Searcher.newWithBuffer(contentBuf);
            String region = searcher.search(ip);
            return parseRegion(region);
        } catch (Exception e) {
            return null;
        }
    }

    // 内网/保留 IP 的关键词无效
    private static final Set<String> SKIP_KEYWORDS = java.util.Set.of(
            "0", "保留地址", "保留", "reserved", "局域网", "内网ip", "本机地址", "未知");

    /**
     * 解析 ip2region 返回的地区字符串。
     * 格式：国家|区域|省份|城市|ISP，eg 中国|0|湖南省|长沙市|电信
     */
    private String parseRegion(String region) {
        if (region == null) return null;
        String[] parts = region.split("\\|");
        if (parts.length < 5) return null;
        String country = parts[0];
        String province = parts[2];

        // 内网/保留地址统一返回 null
        if (containsSkipKeyword(country) || containsSkipKeyword(province)) return null;

        if ("中国".equals(country)) {
            if ("0".equals(province) || province.isBlank()) return null;
            return province
                    .replace("特别行政区", "")
                    .replaceAll("(省|市|壮族自治区|回族自治区|维吾尔自治区|藏族自治区|自治区)$", "");
        } else {
            return ("0".equals(country) || country.isBlank()) ? null : country;
        }
    }

    private boolean containsSkipKeyword(String s) {
        if (s == null || s.isBlank()) return false;
        String lower = s.toLowerCase();
        for (String kw : SKIP_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
