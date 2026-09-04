CREATE TABLE IF NOT EXISTS `note` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL,
    `title`       VARCHAR(100),
    `content`     TEXT,
    `note_type`   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=图文, 2=视频',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=草稿, 1=已发布',
    `source_note_id` BIGINT    NULL COMMENT '草稿来源笔记 id，编辑已发布笔记产生的草稿指向原笔记',
    `like_count`    INT          NOT NULL DEFAULT 0,
    `fav_count`     INT          NOT NULL DEFAULT 0,
    `view_count`    INT          NOT NULL DEFAULT 0,
    `comment_count` INT          NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FULLTEXT KEY `ft_note_title_content` (`title`, `content`) WITH PARSER ngram
);

CREATE TABLE IF NOT EXISTS `note_image` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `note_id`     BIGINT       NOT NULL,
    `url`         VARCHAR(500) NOT NULL,
    `width`       INT          NULL COMMENT '图片宽（客户端上报，瀑布流卡片布局用）',
    `height`      INT          NULL COMMENT '图片高（客户端上报，瀑布流卡片布局用）',
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
    `ip_location`       VARCHAR(50)  NULL         COMMENT 'IP 属地（省份或国家），发布时解析存入',
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

CREATE TABLE IF NOT EXISTS `note_video` (
    `id`                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `note_id`           BIGINT       NOT NULL UNIQUE,
    `original_url`      VARCHAR(500) NOT NULL COMMENT '原始视频(直放模式即播放地址)',
    `cover_url`         VARCHAR(500) NULL COMMENT '封面(客户端上传或截帧)',
    `cover_width`       INT          NULL COMMENT '封面图宽(客户端上报，瀑布流卡片布局用)',
    `cover_height`      INT          NULL COMMENT '封面图高(客户端上报，瀑布流卡片布局用)',
    `video_width`       INT          NOT NULL COMMENT '视频宽(客户端上报)',
    `video_height`      INT          NOT NULL COMMENT '视频高(用于横屏判断)',
    `duration_ms`       BIGINT       NOT NULL COMMENT '时长毫秒(客户端上报)',
    `transcode_status`  TINYINT      NOT NULL DEFAULT 2 COMMENT '直放模式恒为2(可播);0/1/3 仅转码模式使用',
    `hls_master_url`    VARCHAR(500) NULL COMMENT '【转码模式预留】多码率 master.m3u8',
    `hls_levels`        JSON         NULL COMMENT '【转码模式预留】档位信息',
    `fail_reason`       VARCHAR(200) NULL COMMENT '【转码模式预留】',
    `retry_count`       INT          NOT NULL DEFAULT 0 COMMENT '【转码模式预留】',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `note_danmaku` (
    `id`             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `note_id`        BIGINT       NOT NULL COMMENT '视频笔记 id',
    `user_id`        BIGINT       NOT NULL COMMENT '发弹幕用户',
    `content`        VARCHAR(200) NOT NULL COMMENT '弹幕文字',
    `video_time_ms`  BIGINT       NOT NULL COMMENT '弹幕出现时间点(毫秒,相对视频开头)',
    `color`          VARCHAR(7)   NULL COMMENT '弹幕颜色,默认白色,客户端渲染用',
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_danmaku_note_time` (`note_id`, `video_time_ms`)
);

CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL COMMENT '会话所属用户',
    `title`       VARCHAR(100) NOT NULL DEFAULT '' COMMENT '会话标题(首条消息截断)',
    `summary`     TEXT         NULL COMMENT '历史对话摘要(滚动压缩生成)',
    `summarized_until_id` BIGINT NULL COMMENT '已压缩到哪条消息id(<=该id的消息已并入摘要)',
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_ai_conv_user` (`user_id`, `id`)
);

CREATE TABLE IF NOT EXISTS `ai_message` (
    `id`              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `conversation_id` BIGINT       NOT NULL COMMENT '所属会话 id',
    `role`            VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    `content`         TEXT         NOT NULL COMMENT '消息文本',
    `images`          JSON         NULL COMMENT '消息携带的图片 URL 数组',
    `note_id`         BIGINT       NULL COMMENT '用户引用提问的笔记 id',
    `note_snapshot`   TEXT         NULL COMMENT '笔记上下文快照 JSON(发送时检索存入,后续轮次复用)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_ai_msg_conv` (`conversation_id`, `id`)
);

CREATE TABLE IF NOT EXISTS `user_follow` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `follower_id` BIGINT       NOT NULL COMMENT '关注者用户 id',
    `followee_id` BIGINT       NOT NULL COMMENT '被关注者用户 id',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_follow_pair` (`follower_id`, `followee_id`),
    INDEX `idx_follow_follower` (`follower_id`),
    INDEX `idx_follow_followee` (`followee_id`)
);

CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `receiver_id` BIGINT       NOT NULL COMMENT '接收通知的用户 id',
    `sender_id`   BIGINT       NOT NULL COMMENT '触发通知的用户 id',
    `type`        TINYINT      NOT NULL COMMENT '1=点赞笔记 2=收藏 3=评论 4=回复 5=关注 6=点赞评论/回复',
    `note_id`     BIGINT       NULL COMMENT '关联笔记 id',
    `comment_id`  BIGINT       NULL COMMENT '关联评论 id',
    `content`     VARCHAR(200) NULL COMMENT '通知摘要文本',
    `is_read`     TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_notif_receiver` (`receiver_id`, `id`)
);