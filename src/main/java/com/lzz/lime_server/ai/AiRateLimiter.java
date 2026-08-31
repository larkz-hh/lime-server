package com.lzz.lime_server.ai;

import com.lzz.lime_server.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * AI 调用限流：Redis 固定窗口计数，按场景（写作/聊天）区分。
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "ai:rl:";
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 检查并计数，超限抛 BusinessException
     */
    public void check(Long userId, String scene, int perMinute, int perDay) {
        LocalDateTime now = LocalDateTime.now();

        // 分钟窗口
        String minuteKey = KEY_PREFIX + scene + ":" + userId + ":m:" + now.format(MINUTE_FMT);
        Long minuteCount = redisTemplate.opsForValue().increment(minuteKey);
        if (minuteCount != null && minuteCount == 1) {
            redisTemplate.expire(minuteKey, 2, TimeUnit.MINUTES);
        }

        // 日窗口（过期时间 = 到次日 0 点）
        String dayKey = KEY_PREFIX + scene + ":" + userId + ":d:" + now.format(DAY_FMT);
        Long dayCount = redisTemplate.opsForValue().increment(dayKey);
        if (dayCount != null && dayCount == 1) {
            long secondsToTomorrow = ChronoUnit.SECONDS.between(now, LocalDate.now().plusDays(1).atStartOfDay()) + 1;
            redisTemplate.expire(dayKey, secondsToTomorrow, TimeUnit.SECONDS);
        }

        if (minuteCount != null && minuteCount > perMinute) {
            throw new BusinessException("AI 使用太频繁啦，请稍后再试");
        }
        if (dayCount != null && dayCount > perDay) {
            throw new BusinessException("今日 AI 次数已用完，明天再来吧");
        }
    }
}
