CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `username`    VARCHAR(50)  NOT NULL UNIQUE,
    `password`    VARCHAR(255) NOT NULL,
    `email`       VARCHAR(100) UNIQUE,
    `phone`       VARCHAR(20),
    `avatar`      VARCHAR(255),
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'DEFAULT',
    `status`      TINYINT      NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
