-- 知识库模块初始化 SQL (修正版)
-- 执行：mysql -u root -p123456 -h 127.0.0.1 erp < docs/sql/rag_init.sql

-- 文档表
CREATE TABLE IF NOT EXISTS `rag_document` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `file_name`   VARCHAR(255) NOT NULL              COMMENT '文件名',
    `file_type`   VARCHAR(20)  NOT NULL              COMMENT '扩展名',
    `file_size`   BIGINT       NOT NULL DEFAULT 0    COMMENT '文件大小(字节)',
    `doc_type`    VARCHAR(50)  NOT NULL DEFAULT '通用' COMMENT '分类标签',
    `status`      CHAR(1)      NOT NULL DEFAULT '0'  COMMENT '状态(0处理中 1已完成)',
    `chunk_count` INT          NOT NULL DEFAULT 0    COMMENT '分块数',
    `remark`      VARCHAR(500) DEFAULT NULL          COMMENT '备注',
    `create_by`   VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_doc_type` (`doc_type`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

-- 文档分块表
CREATE TABLE IF NOT EXISTS `rag_chunk` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `document_id`   BIGINT       NOT NULL              COMMENT '文档ID',
    `chunk_index`   INT          NOT NULL              COMMENT '分块序号',
    `content`       TEXT         NOT NULL              COMMENT '分块文本',
    `token_count`   INT          DEFAULT 0             COMMENT '估算token数',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块';

-- 注意:AI获客/知识库已改为独立页面(/ai-kit、/knowledge),不再使用 sys_menu 菜单。
-- 原菜单 INSERT 因 is_frame=1 脏数据曾导致登录卡死,已于 2026-07-16 移除,勿再添加。
