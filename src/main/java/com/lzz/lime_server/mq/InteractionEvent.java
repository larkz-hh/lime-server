package com.lzz.lime_server.mq;

import lombok.Data;

// 互动事件消息体
@Data
public class InteractionEvent {

    // 事件唯一 id，消费端 Redis setnx 幂等去重
    private String eventId;

    // 触发者
    private Long senderId;

    // 接收者（通知对象）
    private Long receiverId;

    // 通知类型：1=点赞 2=收藏 3=评论 4=回复 5=关注
    private Integer type;

    private Long noteId;

    private Long commentId;

    // 摘要文本（评论/回复内容），可空
    private String content;
}
