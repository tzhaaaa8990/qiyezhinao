-- 恢复 AI 获客系统菜单(修复 is_frame=1→0、乱码菜单名)
-- 原始数据备份见 deleted-menus-backup-20260716.txt
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
(2000, 'AI获客系统', 0, 5, 'ai-kit', NULL, NULL, 0, 0, 'M', 1, 1, '', 'wechat', 'admin', '2026-07-16 14:27:54', ''),
(2001, '知识库管理', 0, 6, 'rag-knowledge', 'rag/knowledge/index', NULL, 0, 0, 'C', 1, 1, 'ai:rag:list', 'documentation', 'admin', '2026-07-16 14:27:54', ''),
(2005, 'AI经营分析', 2000, 20, 'ai-chat', 'ai/analysis/index', NULL, 0, 0, 'C', 1, 1, 'ai:chat', 'message', 'admin', '2026-07-16 16:39:50', ''),
(2010, '企业微信配置', 2000, 10, 'wechat-config', 'wechat/config/index', NULL, 0, 0, 'C', 1, 1, 'ai:wechat:config', 'edit', 'admin', '2026-07-16 15:10:42', ''),
(2011, 'AI托管设置', 2000, 5, 'wechat-hosting', 'wechat/hosting/index', NULL, 0, 0, 'C', 1, 1, 'ai:wechat:hosting', 'monitor', 'admin', '2026-07-16 15:16:32', ''),
(2012, '客户导入', 2000, 2, 'wechat-customer', 'wechat/customer/index', NULL, 0, 0, 'C', 1, 1, 'ai:wechat:customer', 'user', 'admin', '2026-07-16 15:22:01', '');
