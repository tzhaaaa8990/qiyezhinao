-- ============================================================
-- 创建 AI 只读账号（Task 3：SQL 守卫 + 只读数据源）
-- 应用层 SqlGuard 兜底，数据库层只读账号双重保险
-- ============================================================

CREATE USER IF NOT EXISTS 'ai_readonly'@'localhost' IDENTIFIED BY 'Ai#Readonly2026';

-- 下面 SELECT 生成业务白名单表的 GRANT 语句，逐行执行
SELECT CONCAT('GRANT SELECT ON erp.', table_name, ' TO ''ai_readonly''@''localhost'';')
FROM information_schema.tables
WHERE table_schema = 'erp'
  AND (table_name LIKE 'basic\_%' OR table_name LIKE 'financial\_%'
    OR table_name LIKE 'purchase\_%' OR table_name LIKE 'sales\_%'
    OR table_name LIKE 'wms\_%');

FLUSH PRIVILEGES;
