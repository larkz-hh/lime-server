-- 增量迁移：为已存在的 user 表增加对外公开唯一标识 uid
-- 执行一次即可（对已有数据回填 uid，之后再约束 NOT NULL/UNIQUE）
-- 若已执行过会因列已存在报错，忽略即可

ALTER TABLE `user` ADD COLUMN `uid` VARCHAR(40) NULL UNIQUE COMMENT '对外公开唯一标识，注册时生成、永不修改' AFTER `handle`;

-- 为历史用户回填 uid：u_ + UUID（无横线），每次 UUID() 调用保证唯一
UPDATE `user`
SET `uid` = CONCAT('u_', REPLACE(UUID(), '-', ''))
WHERE `uid` IS NULL OR `uid` = '';

ALTER TABLE `user` MODIFY `uid` VARCHAR(40) NOT NULL UNIQUE;
