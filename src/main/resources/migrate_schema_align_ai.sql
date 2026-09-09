-- 迁移脚本：AI 表结构与实体对齐

ALTER TABLE `ai_conversation`
    ADD COLUMN `client_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '客户端生成的会话业务键(幂等)' AFTER `id`,
    ADD INDEX `idx_ai_conv_client` (`user_id`, `client_id`);

ALTER TABLE `ai_message`
    ADD COLUMN `client_id` VARCHAR(64) NULL COMMENT '客户端生成的消息幂等键' AFTER `id`,
    ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'done' COMMENT 'streaming / done / failed' AFTER `role`,
    ADD INDEX `idx_ai_msg_client` (`client_id`);
