-- 企微AI托管模块
-- mysql -u root -p123456 -h 127.0.0.1 erp < docs/sql/wechat_hosting.sql

CREATE TABLE IF NOT EXISTS `wechat_hosting` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `name`             VARCHAR(100) NOT NULL COMMENT '托管名称',
    `chat_id`          VARCHAR(128) NOT NULL COMMENT '聊天ID（单聊用户ID或群聊ID）',
    `chat_type`        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '类型(0单聊 1群聊)',
    `enabled`          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否启用',
    `interval_seconds` INT          NOT NULL DEFAULT 30 COMMENT '轮询间隔(秒)',
    `auto_reply`       CHAR(1)      NOT NULL DEFAULT '1' COMMENT '是否自动回复',
    `reply_prompt`     TEXT         DEFAULT NULL COMMENT '自定义回复提示词',
    `work_hours_only`  CHAR(1)      NOT NULL DEFAULT '0' COMMENT '仅工作时间',
    `remark`           VARCHAR(500) DEFAULT NULL,
    `create_by`        VARCHAR(64)  DEFAULT '',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`        VARCHAR(64)  DEFAULT '',
    `update_time`      DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_chat_id` (`chat_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微AI托管配置';

CREATE TABLE IF NOT EXISTS `wechat_message_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `hosting_id`    BIGINT       DEFAULT NULL COMMENT '托管ID',
    `msg_id`        VARCHAR(128) DEFAULT '' COMMENT '企微消息ID',
    `from_user`     VARCHAR(128) DEFAULT '' COMMENT '发送者',
    `msg_type`      VARCHAR(20)  DEFAULT 'text' COMMENT '消息类型',
    `content`       TEXT         COMMENT '消息内容',
    `ai_reply`      TEXT         COMMENT 'AI回复',
    `direction`     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '方向(0接收 1回复)',
    `status`        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态(0待处理 1已回复 2已忽略 3需人工)',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_hosting_id` (`hosting_id`),
    INDEX `idx_from_user` (`from_user`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微消息日志';

-- 注意:AI获客/知识库已改为独立页面(/ai-kit、/knowledge),不再使用 sys_menu 菜单。
-- 原菜单 INSERT 因 is_frame=1 脏数据曾导致登录卡死,已于 2026-07-16 移除,勿再添加。
