package com.lzz.lime_server.task;

import com.lzz.lime_server.mapper.NoteViewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/// 定时清理超过 7 天的浏览历史记录，每天凌晨 3 点执行
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewHistoryCleanupTask {

    private final NoteViewMapper noteViewMapper;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanExpiredViewHistory() {
        // 计算7天前的具体时间点
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = noteViewMapper.deleteOlderThan(threshold);
        log.info("浏览历史清理完成，共删除 {} 条超过 7 天的记录", deleted);
    }
}
