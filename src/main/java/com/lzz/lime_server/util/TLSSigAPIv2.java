package com.lzz.lime_server.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

/**
 * 腾讯云 IM UserSig 生成器，算法与官方 tls-sig-api-v2-java 一致：
 * HMAC-SHA256 签名 → JSON 打包 → zlib 压缩 → Base64，+/= 替换为 *-_。
 */
public class TLSSigAPIv2 {

    private final long sdkAppId;

    /** SecretKey 的 UTF-8 字节，直接作为 HMAC 密钥 */
    private final byte[] hmacKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TLSSigAPIv2(long sdkAppId, String secretKey) {
        this.sdkAppId = sdkAppId;
        // 官方算法不做 Base64 解码：byteKey = secretKey.getBytes("UTF-8")
        this.hmacKey = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    /** 生成 UserSig，expireSeconds 为相对有效期（秒），自当前时间起算 */
    public String genSig(String userId, long expireSeconds) {
        long currTime = System.currentTimeMillis() / 1000;
        return genUserSig(userId, currTime, expireSeconds, null);
    }

    private String genUserSig(String userId, long currTime, long expire, byte[] userbuf) {
        // userbuf：附加用户数据，当前未使用，传 null
        Map<String, Object> sigDoc = new LinkedHashMap<>();
        sigDoc.put("TLS.ver", "2.0");
        sigDoc.put("TLS.identifier", userId);
        sigDoc.put("TLS.sdkappid", sdkAppId);
        sigDoc.put("TLS.expire", expire);
        sigDoc.put("TLS.time", currTime);
        sigDoc.put("TLS.sig", hmacsha256(userId, currTime, expire, userbuf));
        if (userbuf != null) {
            sigDoc.put("TLS.userbuf", Base64.getEncoder().encodeToString(userbuf));
        }

        try {
            String json = objectMapper.writeValueAsString(sigDoc);
            String base64 = base64Deflate(json);
            return base64
                    .replace('+', '*')
                    .replace('/', '-')
                    .replace('=', '_');
        } catch (Exception e) {
            throw new IllegalStateException("生成 UserSig 失败", e);
        }
    }

    /**
     * HMAC-SHA256 签名。签名原文是固定格式的纯文本拼接，必须与官方完全一致。
     */
    private String hmacsha256(String identifier, long currTime, long expire, byte[] userbuf) {
        StringBuilder content = new StringBuilder();
        content.append("TLS.identifier:").append(identifier).append("\n");
        content.append("TLS.sdkappid:").append(sdkAppId).append("\n");
        content.append("TLS.time:").append(currTime).append("\n");
        content.append("TLS.expire:").append(expire).append("\n");
        if (userbuf != null) {
            content.append("TLS.userbuf:")
                    .append(Base64.getEncoder().encodeToString(userbuf))
                    .append("\n");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] sig = mac.doFinal(content.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("生成 UserSig 签名失败", e);
        }
    }

    /** zlib 压缩后 Base64 编码 */
    private String base64Deflate(String content) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DeflaterOutputStream dos = new DeflaterOutputStream(bos)) {
            dos.write(content.getBytes(StandardCharsets.UTF_8));
            dos.finish();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("压缩 UserSig 失败", e);
        }
    }
}
