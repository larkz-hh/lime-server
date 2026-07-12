CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `email`       VARCHAR(100) NOT NULL UNIQUE,
    `password`    VARCHAR(255) NOT NULL,
    `nickname`    VARCHAR(50)  NOT NULL,
    `handle`      VARCHAR(30)  NOT NULL UNIQUE,
    `bio`         VARCHAR(200),
    `phone`       VARCHAR(20),
    `avatar`      VARCHAR(255),
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    `status`      TINYINT      NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
