-- 知识库分库改造(2026-07-16)
-- 注意:不包含任何 sys_menu 菜单 INSERT(知识库已改为独立页 /knowledge,不走菜单)

-- 知识库表
CREATE TABLE IF NOT EXISTS `rag_library` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL              COMMENT '知识库名称',
    `description` VARCHAR(500) DEFAULT NULL          COMMENT '描述',
    `chunk_size`  INT          NOT NULL DEFAULT 500  COMMENT '默认分段长度(字符)',
    `separator`   VARCHAR(50)  NOT NULL DEFAULT '\\n\\n' COMMENT '默认分段标识符',
    `create_by`   VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

-- 文档表挂库
ALTER TABLE `rag_document` ADD COLUMN `library_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属知识库' AFTER `id`,
    ADD INDEX `idx_library_id` (`library_id`);

-- 默认库,存量文档归入
INSERT INTO `rag_library`(id, name, description, create_by) VALUES (1, '默认知识库', '系统初始知识库，历史文档自动归入', 'admin');

-- embedding 独立配置(与聊天模型解耦,阿里百炼 OpenAI 兼容接口)
-- 注意 sys_config.config_id 无自增,需显式指定
INSERT INTO `sys_config`(config_id, config_name, config_key, config_value, config_type, create_by, create_time, remark) VALUES
(201, '向量接口地址', 'ai.embedding.baseUrl', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'N', 'admin', NOW(), '知识库向量化接口(OpenAI兼容)'),
(202, '向量API Key', 'ai.embedding.apiKey', '', 'N', 'admin', NOW(), '阿里百炼 API Key'),
(203, '向量模型', 'ai.embedding.model', 'text-embedding-v4', 'N', 'admin', NOW(), '向量化模型名');

-- 废弃旧配置(embedding 曾错误共用 DeepSeek baseUrl,DeepSeek 无 embeddings 接口)
DELETE FROM `sys_config` WHERE config_key = 'ai.embeddingModel';
