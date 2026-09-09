package com.lzz.lime_server.mq;

import com.lzz.lime_server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

// 互动事件消费者
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "interaction-topic", consumerGroup = "lime-notification-group")
public class InteractionEventConsumer implements RocketMQListener<InteractionEvent> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(InteractionEvent event) {
        try {
            notificationService.consumeEvent(event);
        } catch (Exception e) {
            log.error("消费互动事件失败，eventId={}", event == null ? null : event.getEventId(), e);
            throw e;
        }
    }
}
