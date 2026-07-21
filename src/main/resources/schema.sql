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
    `status`      TINYINT      NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);