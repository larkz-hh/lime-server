CREATE TABLE IF NOT EXISTS `note` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL,
    `title`       VARCHAR(100),
    `content`     TEXT,
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=草稿, 1=已发布',
    `like_count`  INT          NOT NULL DEFAULT 0,
    `fav_count`   INT          NOT NULL DEFAULT 0,
    `view_count`  INT          NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `note_image` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `note_id`     BIGINT       NOT NULL,
    `url`         VARCHAR(500) NOT NULL,
    `sort_order`  INT          NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `note_like` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `note_id`     BIGINT       NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_like_note_user` (`note_id`, `user_id`)
);

CREATE TABLE IF NOT EXISTS `note_fav` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `note_id`     BIGINT       NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_fav_note_user` (`note_id`, `user_id`)
);

CREATE TABLE IF NOT EXISTS `note_view` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL,
    `note_id`     BIGINT       NOT NULL,
    `create_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY `uk_view_user_note` (`user_id`, `note_id`)
);

CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `email`       VARCHAR(100) NOT NULL UNIQUE,
    `password`    VARCHAR(255) NOT NULL,
    `nickname`    VARCHAR(50)  NOT NULL,
    `handle`      VARCHAR(30)  NOT NULL UNIQUE,
    `bio`         VARCHAR(200),
    `phone`       VARCHAR(20),
    `avatar`      VARCHAR(255),
    `background_image` VARCHAR(500),
    `gender`      TINYINT,
    `birthday`    DATE,
    `region`      VARCHAR(50),
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    `like_private` TINYINT     NOT NULL DEFAULT 0 COMMENT '点赞列表是否私密：0=公开，1=私密',
    `fav_private`  TINYINT     NOT NULL DEFAULT 0 COMMENT '收藏列表是否私密：0=公开，1=私密',
    `status`      TINYINT      NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `note_comment` (
    `id`                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `note_id`           BIGINT       NOT NULL,
    `user_id`           BIGINT       NOT NULL,
    `parent_id`         BIGINT       NULL         COMMENT 'null=一级评论，非null=回复（指向一级评论id）',
    `reply_to_user_id`  BIGINT       NULL         COMMENT '回复的目标用户id，仅回复时有值',
    `content`           TEXT         NULL,
    `voice_url`         VARCHAR(500) NULL,
    `voice_duration`    INT          NULL         COMMENT '语音时长（秒）',
    `like_count`        INT          NOT NULL DEFAULT 0,
    `reply_count`       INT          NOT NULL DEFAULT 0  COMMENT '仅一级评论有效',
    `hot_score`         INT          NOT NULL DEFAULT 0  COMMENT '热度分=like_count+reply_count*2',
    `ip_address`        VARCHAR(64)  NULL,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_comment_note_parent_time` (`note_id`, `parent_id`, `id`),
    INDEX `idx_comment_note_hot`         (`note_id`, `parent_id`, `hot_score`, `id`)
);

CREATE TABLE IF NOT EXISTS `note_comment_image` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `comment_id`  BIGINT       NOT NULL,
    `url`         VARCHAR(500) NOT NULL,
    `sort_order`  INT          NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_comment_image_comment` (`comment_id`)
);

CREATE TABLE IF NOT EXISTS `comment_like` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `comment_id`  BIGINT       NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_comment_like_comment_user` (`comment_id`, `user_id`)
);