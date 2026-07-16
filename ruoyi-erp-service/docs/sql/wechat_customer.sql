-- 企微客户导入
-- mysql -u root -p123456 -h 127.0.0.1 erp < docs/sql/wechat_customer.sql

CREATE TABLE IF NOT EXISTS `wechat_customer` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `external_userid`  VARCHAR(64)  NOT NULL COMMENT '企微外部联系人ID',
    `name`             VARCHAR(60)  NOT NULL COMMENT '名称',
    `avatar`           VARCHAR(512) DEFAULT '' COMMENT '头像',
    `type`             TINYINT      DEFAULT 1 COMMENT '类型 1-微信用户 2-企业用户',
    `gender`           TINYINT      DEFAULT 0,
    `corp_name`        VARCHAR(100) DEFAULT '' COMMENT '所在企业',
    `mobile`           VARCHAR(13)  DEFAULT '' COMMENT '手机',
    `email`            VARCHAR(50)  DEFAULT '',
    `address`          VARCHAR(200) DEFAULT '',
    `remark`           VARCHAR(255) DEFAULT '',
    `add_time`         DATETIME     DEFAULT NULL COMMENT '添加时间',
    `erp_merchant_id`  BIGINT       DEFAULT NULL COMMENT '同步到ERP的商户ID',
    `synced`           CHAR(1)      DEFAULT '0' COMMENT '是否已同步 0-否 1-是',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_external_userid` (`external_userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微客户';

-- 注意:AI获客/知识库已改为独立页面(/ai-kit、/knowledge),不再使用 sys_menu 菜单。
-- 原菜单 INSERT 因 is_frame=1 脏数据曾导致登录卡死,已于 2026-07-16 移除,勿再添加。
