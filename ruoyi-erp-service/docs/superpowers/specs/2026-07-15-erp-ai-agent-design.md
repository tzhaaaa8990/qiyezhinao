# ERP AI 智能助手（ruoyi-ai 模块）设计文档

日期：2026-07-15
状态：待用户审阅

## 1. 背景与目标

在现有 RuoYi ERP 上做二次开发，加入 AI 智能助手，最终商业化交付企业客户。
用户用自然语言提问，AI 自动决定调用哪些 ERP 能力（意图识别 + 工具调用），
权限完全复用 RuoYi 现有 RBAC——不同角色能用的 AI 工具不同。

**MVP 场景：老板经营分析** —— 老板一句话查经营数据
（"这个月销售额多少""哪个商品卖得最好"），AI 生成 SQL 查库（只读）并回答。

暂不做：知识库/RAG（后续补充）、MaxKB/Dify 等外部平台、多智能体图编排。

## 2. 技术选型

| 项 | 选择 | 理由 |
|---|---|---|
| Agent 框架 | Spring AI | 与现有 Java/Spring 栈统一；同进程内 Sa-Token 上下文直接可用，无需 token 透传；概念与 LangChain 一一对应 |
| 大模型 | 云 API（DeepSeek 或其他 OpenAI 兼容接口） | 起步成本低；Spring AI 换模型只改配置，后续可切私有化 |
| 落地位置 | `ruoyi-erp-service/ruoyi-modules/ruoyi-ai` 新 Maven 模块 | 打进同一个 jar，部署交付简单；日后可拆独立服务 |
| 可观测 | Langfuse（Docker 自部署，经 OpenTelemetry 接入） | 可视化每次对话的工具调用链路、token、耗时；可审计 |

## 3. 架构

```
ERP 前端（Vue）
  └─ AI 聊天入口（带用户 Sa-Token 调 /ai/chat，SSE 流式）
        │
        ▼
ruoyi-ai 模块（与 ERP 同进程）
  ├─ AiChatController   /ai/chat（SSE）
  ├─ AgentService       ChatClient + 工具集（tool-call 自动循环）
  ├─ tools/
  │    ├─ SalesAnalysisTool   @Tool 经营分析：text2SQL → 只读数据源执行
  │    └─ （后续按需增加：查库存、查订单……）
  └─ config/
       ├─ ChatClientConfig    模型配置
       └─ ToolAccessConfig    按 RuoYi 角色/权限字符串过滤可用工具集
        │
        ▼
MySQL erp 库（text2SQL 使用独立只读账号 + 表白名单）
```

## 4. 权限模型（核心设计）

1. 用户登录 ERP → 携带 Sa-Token 调 `/ai/chat`，AI 与业务同进程，
   `LoginHelper` 直接拿到当前用户、角色、权限字符串。
2. **工具集按角色下发**：构造 ChatClient 时只挂当前用户有权限的 @Tool。
   工具与 RuoYi 权限字符串对应（如 `ai:tool:salesAnalysis`），
   老板通过现有"角色管理"界面分配，即用户所说的"权限表"，无需新建表。
3. **text2SQL 双保险**：
   - 应用层：`ai:tool:salesAnalysis` 权限（默认仅老板角色）
   - 数据层：独立 MySQL 只读账号，仅授权业务统计相关表（白名单），
     敏感表（如 sys_user 密码字段）不可见
4. 工具内部若调 Service 层方法，现有 `@SaCheckPermission` 自然生效（同线程上下文）。

## 5. MVP 交付物

1. `ruoyi-ai` Maven 模块骨架（依赖 Spring AI，注册进父 pom）
2. `/ai/chat` SSE 流式对话接口（Sa-Token 鉴权）
3. `SalesAnalysisTool`：text2SQL（提示词内嵌业务表结构说明）→ 只读执行 → 返回结果
4. 按角色过滤工具集的机制
5. 前端：ERP 内 AI 聊天页面（老板菜单"AI 经营分析"，SSE 渲染）
6. Langfuse 接入（Docker 起 Langfuse，Spring AI OTel 导出）

验收标准：admin（老板角色）登录 → AI 页面提问"本月销售额是多少" →
Langfuse 中可见完整链路 → 回答与 SQL 直查结果一致；
无 `ai:tool:salesAnalysis` 权限的测试账号提问同样问题 → AI 明确告知无权限。

## 6. 演进方向（不在 MVP 范围）

- 知识库/RAG（Spring AI VectorStore 或后接 MaxKB）
- 全员悬浮聊天窗（第二个入口，绑查单/查库存工具）
- 写操作工具（AI 开单等，需二次确认机制）
- 多智能体编排（Spring AI Alibaba Graph，可导出 Mermaid 流程图）
- 模型私有化部署（Ollama/vLLM，改配置即可切换）
