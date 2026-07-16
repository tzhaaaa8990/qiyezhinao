-- 企业微信模块初始化
-- mysql -u root -p123456 -h 127.0.0.1 erp < docs/sql/wechat_init.sql

CREATE TABLE IF NOT EXISTS `wechat_config` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `corp_id`          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '企业ID',
    `corp_secret`      VARCHAR(256) NOT NULL DEFAULT '' COMMENT '应用Secret',
    `agent_id`         INT          NOT NULL DEFAULT 0 COMMENT '应用AgentId',
    `token`            VARCHAR(64)  DEFAULT '' COMMENT '回调Token',
    `encoding_aes_key` VARCHAR(128) DEFAULT '' COMMENT '回调EncodingAESKey',
    `webhook_url`      VARCHAR(512) DEFAULT '' COMMENT '群机器人Webhook',
    `status`           CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态(0停用 1启用)',
    `remark`           VARCHAR(500) DEFAULT NULL,
    `create_by`        VARCHAR(64)  DEFAULT '',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`        VARCHAR(64)  DEFAULT '',
    `update_time`      DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业微信配置';

-- 注意:AI获客/知识库已改为独立页面(/ai-kit、/knowledge),不再使用 sys_menu 菜单。
-- 原菜单 INSERT 因 is_frame=1 脏数据曾导致登录卡死,已于 2026-07-16 移除,勿再添加。
