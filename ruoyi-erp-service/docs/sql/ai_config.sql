INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_by, create_time, remark)
VALUES (100, 'AI-接口地址', 'ai.baseUrl', 'https://api.deepseek.com', 'Y', 'admin', NOW(), 'OpenAI兼容接口地址'),
       (101, 'AI-ApiKey',  'ai.apiKey',  '', 'Y', 'admin', NOW(), '大模型 API Key（前端系统配置里填写）'),
       (102, 'AI-模型名',  'ai.model',   'deepseek-chat', 'Y', 'admin', NOW(), '模型名称');
