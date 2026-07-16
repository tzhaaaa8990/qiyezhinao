# ruoyi-ai 模块 MVP 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 RuoYi ERP 中新增 ruoyi-ai 模块，实现"老板一句话查经营数据"的 AI Agent（text2SQL 只读），前端提供聊天入口。

**Architecture:** Spring AI（同进程 Maven 模块）+ @Tool 工具集按 RuoYi 权限过滤 + 独立只读数据源执行 AI 生成的 SELECT + 前端 SSE 聊天页。

**Tech Stack:** Spring Boot 3.2.6 / Java 17 / Spring AI 1.0.1 / Sa-Token 1.37 / DeepSeek API（OpenAI 兼容）/ Vue3 + Element Plus

## Global Constraints

- Maven 多模块，`revision=5.2.0`，父工程 `com.ruoyi`
- 启动必须 `--spring.profiles.active=dev`；运行时配置在 `ruoyi-admin-erp/config/application-dev.yml`（jar 外部，改配置不用重打包）
- 后端三层规范：Controller >> Service（本模块无 Repository 需求，text2SQL 用 JdbcTemplate）
- AI 生成的 SQL 只允许 SELECT，双保险：应用层校验 + MySQL 只读账号（表白名单：`basic_*`/`financial_*`/`purchase_*`/`sales_*`/`wms_*`，禁止 `sys_*`）
- 前端 token 头：`Authorization: Bearer <token>`（见 `src/utils/request.js:32`）
- 数据库 erp，root/123456（开发机）；只读账号 `ai_readonly` 在 Task 3 创建

---

### Task 1: 创建 ruoyi-ai 模块骨架并注册

**Files:**
- Modify: `ruoyi-modules/pom.xml`（加 module）
- Create: `ruoyi-modules/ruoyi-ai/pom.xml`
- Create: `ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/package-info.java`
- Modify: `ruoyi-admin-erp/pom.xml`（加依赖）

**Interfaces:**
- Produces: Maven 模块 `com.ruoyi:ruoyi-ai`，包根 `com.ruoyi.ai`（在主应用扫包范围 `com.ruoyi` 内，无需额外配置）

- [ ] **Step 1: ruoyi-modules/pom.xml 注册子模块**

```xml
    <modules>
        <module>ruoyi-generator</module>
        <module>ruoyi-system</module>
        <module>ruoyi-ai</module>
    </modules>
```

- [ ] **Step 2: 新建 ruoyi-modules/ruoyi-ai/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>ruoyi-modules</artifactId>
        <groupId>com.ruoyi</groupId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>ruoyi-ai</artifactId>

    <description>
        AI 智能体模块
    </description>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.0.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>ruoyi-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>ruoyi-common-satoken</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>ruoyi-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 建包占位** `src/main/java/com/ruoyi/ai/package-info.java`

```java
/**
 * AI 智能体模块：ChatClient + 工具集（按 RuoYi 权限过滤）
 */
package com.ruoyi.ai;
```

- [ ] **Step 4: ruoyi-admin-erp/pom.xml 加依赖**（放在 ruoyi-system 依赖后面）

```xml
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>ruoyi-ai</artifactId>
            <version>${revision}</version>
        </dependency>
```

- [ ] **Step 5: 验证编译**

Run: `cd C:/Users/asus/Desktop/ruoyierp/ruoyi-erp-service && mvn compile -pl ruoyi-modules/ruoyi-ai -am -DskipTests -q`
Expected: BUILD SUCCESS（首次需下载 Spring AI 依赖）

- [ ] **Step 6: Commit**

```bash
git add ruoyi-modules/pom.xml ruoyi-modules/ruoyi-ai ruoyi-admin-erp/pom.xml
git commit -m "feat(ai): 新建 ruoyi-ai 模块骨架并注册到工程"
```

---

### Task 2: AI 配置存 sys_config + 动态构建模型（前端可改，即时生效）

**Files:**
- Create: `docs/sql/ai_config.sql`（sys_config 三个参数）
- Create: `ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/config/AiModelFactory.java`
- Modify: `ruoyi-modules/ruoyi-ai/pom.xml`（追加 ruoyi-system 依赖，用 ISysConfigService 读参数）

**Interfaces:**
- Consumes: `ISysConfigService.selectConfigByKey(String)`（ruoyi-system 现有，带缓存）
- Produces: `AiModelFactory.createChatClient()` → 每次调用按 sys_config 当前值构建 ChatClient（改 key/模型无需重启）

- [ ] **Step 1: ai_config.sql 写入并执行**

```sql
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_by, create_time, remark)
VALUES (100, 'AI-接口地址', 'ai.baseUrl', 'https://api.deepseek.com', 'Y', 'admin', NOW(), 'OpenAI兼容接口地址'),
       (101, 'AI-ApiKey',  'ai.apiKey',  '', 'Y', 'admin', NOW(), '大模型 API Key（前端系统配置里填写）'),
       (102, 'AI-模型名',  'ai.model',   'deepseek-chat', 'Y', 'admin', NOW(), '模型名称');
```

Run: `mysql -uroot -p123456 erp < docs/sql/ai_config.sql`

- [ ] **Step 2: ruoyi-ai/pom.xml 追加依赖**

```xml
        <dependency>
            <groupId>com.ruoyi</groupId>
            <artifactId>ruoyi-system</artifactId>
            <version>${revision}</version>
        </dependency>
```

- [ ] **Step 3: AiModelFactory.java**

```java
package com.ruoyi.ai.config;

import com.ruoyi.system.service.ISysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * 按 sys_config 当前值动态构建 ChatClient。
 * 前端"系统配置"改参数后即时生效（sys_config 有缓存，改动会刷新），无需重启。
 */
@Component
@RequiredArgsConstructor
public class AiModelFactory {

    private final ISysConfigService configService;

    public ChatClient createChatClient() {
        String baseUrl = configService.selectConfigByKey("ai.baseUrl");
        String apiKey = configService.selectConfigByKey("ai.apiKey");
        String model = configService.selectConfigByKey("ai.model");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先在右上角[系统配置]中填写 AI ApiKey");
        }
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.2).build())
            .build();
        return ChatClient.create(chatModel);
    }
}
```

注意：不配置 `spring.ai.openai.*` 属性，避免 starter 自动装配再建一个模型 Bean。若启动时自动装配因缺 api-key 报错，在 `application.yml` 排除：
`spring.autoconfigure.exclude: org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration`

- [ ] **Step 4: 冒烟验证（临时，不提交）**

先用 SQL 把真实 key 写入：`UPDATE sys_config SET config_value='sk-xxx' WHERE config_key='ai.apiKey';`
临时 `AiSmokeRunner`（ApplicationRunner 注入 AiModelFactory，`createChatClient().prompt().user("回复OK").call().content()` 打日志），看到回复后**删除该文件**。

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-ai docs/sql/ai_config.sql
git commit -m "feat(ai): AI 模型参数存 sys_config，动态构建 ChatClient"
```

---

### Task 3: SQL 守卫（TDD）+ 只读数据源 + 经营分析工具

**Files:**
- Create: `ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/tools/SqlGuard.java`
- Test:   `ruoyi-modules/ruoyi-ai/src/test/java/com/ruoyi/ai/tools/SqlGuardTest.java`
- Create: `ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/config/ReadonlyDataSourceConfig.java`
- Create: `ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/tools/SalesAnalysisTool.java`
- Modify: `ruoyi-admin-erp/config/application-dev.yml`（只读数据源连接）

**Interfaces:**
- Consumes: 无
- Produces: `SqlGuard.assertSafeSelect(String sql)` 静态方法（非法抛 `IllegalArgumentException`）；`SalesAnalysisTool`（带 `@Tool` 方法 `executeAnalysisQuery(String sql)`，供 Task 4 挂载）

- [ ] **Step 1: 写失败测试 SqlGuardTest.java**

```java
package com.ruoyi.ai.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlGuardTest {

    @Test
    void allowsPlainSelect() {
        assertDoesNotThrow(() -> SqlGuard.assertSafeSelect(
            "SELECT SUM(total_amount) FROM sales_order WHERE create_time >= '2026-07-01'"));
    }

    @Test
    void rejectsNonSelect() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("UPDATE sales_order SET total_amount = 0"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("DELETE FROM sales_order"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("DROP TABLE sales_order"));
    }

    @Test
    void rejectsMultiStatementAndComment() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT 1; DELETE FROM sales_order"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT 1 -- 注释"));
    }

    @Test
    void rejectsSysTables() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT password FROM sys_user"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl ruoyi-modules/ruoyi-ai -am -Dtest=SqlGuardTest -q`
Expected: FAIL（SqlGuard 不存在，编译错误）

- [ ] **Step 3: 实现 SqlGuard.java**

```java
package com.ruoyi.ai.tools;

import java.util.regex.Pattern;

/**
 * AI 生成 SQL 的应用层守卫：仅允许单条 SELECT，禁注释/多语句/系统表。
 * 数据层另有只读账号兜底。
 */
public final class SqlGuard {

    private static final Pattern FORBIDDEN = Pattern.compile(
        "(;|--|/\\*|#)|\\b(update|delete|insert|drop|alter|truncate|grant|create|replace|call|load|outfile)\\b|\\bsys_\\w+",
        Pattern.CASE_INSENSITIVE);

    private SqlGuard() {
    }

    public static void assertSafeSelect(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        if (!trimmed.toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("仅允许 SELECT 查询");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("SQL 含禁止的关键字/注释/多语句/系统表");
        }
    }
}
```

- [ ] **Step 4: 跑测试通过**

Run: `mvn test -pl ruoyi-modules/ruoyi-ai -am -Dtest=SqlGuardTest -q`
Expected: Tests run: 4, Failures: 0

- [ ] **Step 5: 创建 MySQL 只读账号（一次性运维操作，SQL 存档到 `docs/sql/ai_readonly.sql`）**

```sql
CREATE USER IF NOT EXISTS 'ai_readonly'@'localhost' IDENTIFIED BY 'Ai#Readonly2026';
-- 生成业务表白名单授权（先执行下面 SELECT，把结果逐行执行）
SELECT CONCAT('GRANT SELECT ON erp.', table_name, ' TO ''ai_readonly''@''localhost'';')
FROM information_schema.tables
WHERE table_schema = 'erp'
  AND (table_name LIKE 'basic\_%' OR table_name LIKE 'financial\_%'
    OR table_name LIKE 'purchase\_%' OR table_name LIKE 'sales\_%'
    OR table_name LIKE 'wms\_%');
FLUSH PRIVILEGES;
```

验证：`mysql -uai_readonly -p'Ai#Readonly2026' -e "SELECT COUNT(*) FROM erp.sales_order; DELETE FROM erp.sales_order;"` → SELECT 成功、DELETE 报 denied

- [ ] **Step 6: 只读数据源配置**

`ruoyi-admin-erp/config/application-dev.yml` 追加：

```yaml
--- # AI 只读数据源（不走 dynamic-datasource，独立小连接池）
ai:
  datasource:
    url: jdbc:mysql://localhost:3306/erp?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8
    username: ai_readonly
    password: Ai#Readonly2026
```

`ReadonlyDataSourceConfig.java`：

```java
package com.ruoyi.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ReadonlyDataSourceConfig {

    @Bean(name = "aiReadonlyJdbcTemplate")
    public JdbcTemplate aiReadonlyJdbcTemplate(
        @Value("${ai.datasource.url}") String url,
        @Value("${ai.datasource.username}") String username,
        @Value("${ai.datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(4);
        ds.setReadOnly(true);
        JdbcTemplate template = new JdbcTemplate(ds);
        template.setQueryTimeout(15);
        template.setMaxRows(200);
        return template;
    }
}
```

- [ ] **Step 7: SalesAnalysisTool.java**

```java
package com.ruoyi.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 经营分析工具：执行 AI 生成的只读 SQL。
 * 权限字符串 ai:tool:salesAnalysis，由 AgentService 按用户过滤后挂载。
 */
@Component
public class SalesAnalysisTool {

    public static final String PERM = "ai:tool:salesAnalysis";

    private final JdbcTemplate jdbcTemplate;

    public SalesAnalysisTool(@Qualifier("aiReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "执行经营数据分析 SQL（仅 SELECT）。用于回答销售额、销量排行、库存、采购、财务流水等统计问题。返回查询结果列表。")
    public List<Map<String, Object>> executeAnalysisQuery(
        @ToolParam(description = "单条 MySQL SELECT 语句，不带分号") String sql) {
        SqlGuard.assertSafeSelect(sql);
        return jdbcTemplate.queryForList(sql);
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add ruoyi-modules/ruoyi-ai docs/sql/ai_readonly.sql
git commit -m "feat(ai): text2SQL 只读工具（SqlGuard 双保险 + 独立只读数据源）"
```

---

### Task 4: AgentService + /ai/chat SSE 接口（按权限挂工具）

**Files:**
- Create: `ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/service/AgentService.java`
- Create: `ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/controller/AiChatController.java`

**Interfaces:**
- Consumes: `AiModelFactory.createChatClient()`（Task 2）、`SalesAnalysisTool` 及其 `PERM` 常量（Task 3）
- Produces: `GET /ai/chat?message=...`，`text/event-stream` 流式返回；登录即可访问，无工具权限时 AI 无工具可用

- [ ] **Step 1: AgentService.java**

```java
package com.ruoyi.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.ruoyi.ai.config.AiModelFactory;
import com.ruoyi.ai.tools.SalesAnalysisTool;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
        你是智辉科技企业智能体，一个 ERP 经营分析助手。今天是 %s。
        数据库为 MySQL，业务表（只读）：
        sales_order(销售订单: id, merchant_id 客户, total_amount 金额, create_time),
        sales_order_detail(销售明细: order_id, sku_id, quantity 数量, amount 金额),
        purchase_order(采购订单: id, merchant_id 供应商, total_amount, create_time),
        wms_inventory(库存: sku_id, warehouse_id, quantity),
        basic_goods(商品: id, goods_name), basic_sku(id, goods_id, sku_name),
        basic_warehouse(仓库: id, warehouse_name), basic_merchant(往来单位: id, merchant_name),
        financial_trans_history(资金流水: amount, trans_time)。
        回答经营问题时：先思考需要的 SQL，调用工具查询，再用简洁中文总结数据结论。
        金额单位默认为元。如果没有可用工具，直接告知用户"你没有经营分析权限"。
        """;

    private final AiModelFactory modelFactory;
    private final SalesAnalysisTool salesAnalysisTool;

    public AgentService(AiModelFactory modelFactory, SalesAnalysisTool salesAnalysisTool) {
        this.modelFactory = modelFactory;
        this.salesAnalysisTool = salesAnalysisTool;
    }

    /**
     * 核心权限设计：按当前登录用户的 RuoYi 权限字符串过滤可挂载的工具集
     */
    private Object[] toolsForCurrentUser() {
        List<Object> tools = new ArrayList<>();
        if (StpUtil.hasPermission(SalesAnalysisTool.PERM)) {
            tools.add(salesAnalysisTool);
        }
        return tools.toArray();
    }

    public Flux<String> chat(String message) {
        return modelFactory.createChatClient().prompt()
            .system(SYSTEM_PROMPT.formatted(LocalDate.now()))
            .user(message)
            .tools(toolsForCurrentUser())
            .stream()
            .content();
    }
}
```

- [ ] **Step 2: AiChatController.java**

```java
package com.ruoyi.ai.controller;

import com.ruoyi.ai.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 对话接口（Sa-Token 全局拦截器已保证登录才能访问）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class AiChatController {

    private final AgentService agentService;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String message) {
        return agentService.chat(message);
    }
}
```

- [ ] **Step 3: 打包启动，命令行验证**

Run:
```bash
mvn package -pl ruoyi-admin-erp -am -DskipTests -q
java -jar ruoyi-admin-erp/target/ruoyi-admin-erp.jar --spring.profiles.active=dev
# 另开终端：先登录拿 token（验证码需临时从页面拿，或用已登录浏览器的 token）
curl -N "http://localhost:8080/ai/chat?message=本月销售额是多少" -H "Authorization: Bearer <token>"
```
Expected: SSE 流式输出，内容包含查询得到的销售额数字；Langfuse/日志可见工具调用了 SELECT SUM…

- [ ] **Step 4: Commit**

```bash
git add ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/service ruoyi-modules/ruoyi-ai/src/main/java/com/ruoyi/ai/controller
git commit -m "feat(ai): /ai/chat SSE 对话接口，工具集按 RuoYi 权限动态挂载"
```

---

### Task 5: 权限字符串与菜单 SQL

**Files:**
- Create: `docs/sql/ai_menu.sql`（执行进库）

**Interfaces:**
- Consumes: `ai:tool:salesAnalysis`（Task 3 定义）
- Produces: 菜单"AI 经营分析"（component `ai/analysis/index`）+ 权限按钮，老板在"系统管理→角色管理"给角色勾选即完成授权

- [ ] **Step 1: 写并执行菜单 SQL**

```sql
-- 一级菜单：AI 助手
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (2000, 'AI 助手', 0, 5, 'ai', NULL, 1, 0, 'M', '0', '0', '', 'robot', 'admin', NOW(), 'AI 智能体');
-- 二级页面：AI 经营分析
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (2001, 'AI 经营分析', 2000, 1, 'analysis', 'ai/analysis/index', 1, 0, 'C', '0', '0', 'ai:tool:salesAnalysis', 'chart', 'admin', NOW(), '老板经营分析');
```

Run: `mysql -uroot -p123456 erp < docs/sql/ai_menu.sql`
Expected: admin 登录后左侧出现"AI 助手 → AI 经营分析"菜单（admin 超管天然全权限）

- [ ] **Step 2: Commit**

```bash
git add docs/sql/ai_menu.sql
git commit -m "feat(ai): AI 经营分析菜单与权限字符串"
```

---

### Task 6: 前端 AI 聊天页面（SSE）

**Files:**
- Create: `ruoyi-erp-vue/src/views/ai/analysis/index.vue`

**Interfaces:**
- Consumes: `GET /ai/chat`（SSE，需 `Authorization: Bearer` 头，故用 fetch 流而非 EventSource）；`import.meta.env.VITE_APP_BASE_API`；`getToken()` 来自 `@/utils/auth`
- Produces: 菜单页 `ai/analysis/index`（对应 Task 5 的 component 路径）

- [ ] **Step 1: index.vue（对话界面，样式对齐 Element Plus 风格）**

```vue
<template>
  <div class="app-container ai-chat">
    <div class="msg-list" ref="listRef">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">{{ m.content }}</div>
      </div>
      <div v-if="loading" class="msg assistant"><div class="bubble">思考中…</div></div>
    </div>
    <div class="input-bar">
      <el-input v-model="input" placeholder="问点经营问题，比如：本月销售额是多少？"
        @keyup.enter="send" :disabled="loading" clearable />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup name="AiAnalysis">
import { getToken } from '@/utils/auth'

const messages = ref([])
const input = ref('')
const loading = ref(false)
const listRef = ref()

async function send() {
  const q = input.value.trim()
  if (!q || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: q })
  const reply = reactive({ role: 'assistant', content: '' })
  messages.value.push(reply)
  loading.value = true
  try {
    const url = import.meta.env.VITE_APP_BASE_API + '/ai/chat?message=' + encodeURIComponent(q)
    const resp = await fetch(url, { headers: { Authorization: 'Bearer ' + getToken() } })
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      // SSE 帧格式：data:xxx\n\n
      const frames = buf.split('\n\n')
      buf = frames.pop()
      for (const f of frames) {
        for (const line of f.split('\n')) {
          if (line.startsWith('data:')) reply.content += line.slice(5)
        }
      }
      await nextTick()
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  } catch (e) {
    reply.content = reply.content || '请求失败：' + e.message
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.ai-chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 130px);
  .msg-list {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    .msg {
      display: flex;
      margin-bottom: 12px;
      &.user { justify-content: flex-end; .bubble { background: #409eff; color: #fff; } }
      &.assistant { justify-content: flex-start; .bubble { background: #f4f4f5; } }
      .bubble {
        max-width: 70%;
        padding: 10px 14px;
        border-radius: 8px;
        white-space: pre-wrap;
        word-break: break-word;
        line-height: 1.6;
      }
    }
  }
  .input-bar {
    display: flex;
    gap: 8px;
    padding: 12px 16px;
    border-top: 1px solid var(--el-border-color-light);
  }
}
</style>
```

- [ ] **Step 2: 手动验证（端到端）**

Run: 前端 `npm run dev`，admin 登录 → "AI 助手 → AI 经营分析" → 问"本月销售额是多少"
Expected: 流式输出答案；用 `mysql -uroot -p123456 erp -e "SELECT SUM(total_amount) FROM sales_order WHERE ..."` 对照数字一致

- [ ] **Step 3: 权限反向验证**

用无 `ai:tool:salesAnalysis` 权限的账号（如 erp 用户，先确认其角色未勾选该菜单）调 `/ai/chat` 问同样问题
Expected: AI 回答"你没有经营分析权限"（无工具可用），且日志无 SQL 执行

- [ ] **Step 4: Commit**

```bash
git add src/views/ai/analysis/index.vue
git commit -m "feat(ai): AI 经营分析聊天页面（SSE 流式）"
```

---

### Task 7: 右上角"系统配置"弹窗（前端管理 AI 参数）

**Files:**
- Create: `ruoyi-erp-vue/src/layout/components/SystemConfigDialog.vue`
- Modify: `ruoyi-erp-vue/src/layout/components/Navbar.vue`（right-menu 区加入口图标）

**Interfaces:**
- Consumes: `getConfigKey(key)` / `updateConfigByKey(key, value)`（`@/api/system/config` 现有方法，后者对应 `PUT /system/config/updateByKey`）
- Produces: 右上角齿轮图标 → 弹窗表单（接口地址/ApiKey/模型名）→ 保存即写 sys_config，后端 AiModelFactory 下次对话即用新值

- [ ] **Step 1: SystemConfigDialog.vue**

```vue
<template>
  <el-dialog v-model="visible" title="系统配置" width="480px" append-to-body>
    <el-form :model="form" label-width="90px">
      <el-form-item label="接口地址">
        <el-input v-model="form['ai.baseUrl']" placeholder="https://api.deepseek.com" />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="form['ai.apiKey']" type="password" show-password placeholder="sk-..." />
      </el-form-item>
      <el-form-item label="模型名称">
        <el-input v-model="form['ai.model']" placeholder="deepseek-chat" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { getConfigKey, updateConfigByKey } from '@/api/system/config'
import { ElMessage } from 'element-plus'

const visible = ref(false)
const saving = ref(false)
const KEYS = ['ai.baseUrl', 'ai.apiKey', 'ai.model']
const form = reactive({})

async function open() {
  for (const k of KEYS) {
    const res = await getConfigKey(k)
    form[k] = res.data ?? res.msg ?? ''
  }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    for (const k of KEYS) {
      await updateConfigByKey(k, form[k] ?? '')
    }
    ElMessage.success('保存成功，立即生效')
    visible.value = false
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
```

- [ ] **Step 2: Navbar.vue 的 `right-menu` 区（头像下拉之前）加入口，仅有参数修改权限者可见**

```vue
      <div class="right-menu-item hover-effect" v-hasPermi="['system:config:edit']"
           @click="configDialogRef.open()" title="系统配置">
        <svg-icon icon-class="system" />
      </div>
      <system-config-dialog ref="configDialogRef" />
```

script 部分：

```js
import SystemConfigDialog from './SystemConfigDialog.vue'
const configDialogRef = ref()
```

- [ ] **Step 3: 手动验证**

admin 登录 → 右上角出现齿轮图标 → 打开填 DeepSeek Key 保存 → 到"AI 经营分析"提问成功；
用无 `system:config:edit` 权限账号登录 → 图标不可见。
注意 `getConfigKey` 返回结构以实际响应为准（RuoYi 该接口把值放在 `msg` 或 `data`，联调时确认取哪个字段）。

- [ ] **Step 4: Commit**

```bash
git add src/layout/components/SystemConfigDialog.vue src/layout/components/Navbar.vue
git commit -m "feat(ai): 右上角系统配置弹窗，前端管理 AI 模型参数"
```

---

### Task 8: 品牌化：去广告、换 logo、改标题

**Files:**
- Modify: `ruoyi-erp-vue/.env.development` / `.env.production`（VITE_APP_TITLE）
- Modify: `ruoyi-erp-vue/index.html`（title）
- Modify: `ruoyi-erp-vue/src/layout/components/Navbar.vue`、`src/views/login.vue`、`src/views/register.vue`（删"程序员诚哥"及推广内容）
- Replace: `ruoyi-erp-vue/src/assets/logo/logo.png`（★ 需用户提供 logo 图片文件路径）
- Delete: `ruoyi-erp-vue/src/assets/logo/gzh.jpg`（公众号二维码，属广告）
- DB: `UPDATE sys_user SET nick_name='管理员' WHERE user_id=1;`

- [ ] **Step 1:** 全局搜索 `程序员诚哥|诚哥|gitee.com/zccbbg|公众号|gzh` 逐处删除/替换（涉及上述 3 个 vue 文件，删除后确认无残留：`grep -rn "诚哥\|zccbbg" src/`）
- [ ] **Step 2:** 标题三处统一改为 `智辉科技企业智能体`（.env.development、.env.production 的 VITE_APP_TITLE、index.html `<title>`）
- [ ] **Step 3:** 用户 logo 转 PNG 并覆盖（保持文件名 logo.png 不变，引用自动生效）：

```bash
python -X utf8 -c "
from PIL import Image
im = Image.open('C:/Users/asus/Desktop/电商设计/店铺/店铺首页素材/17225626-3a05-4507-a812-bec7cf28a703.jpg')
im.save('C:/Users/asus/Desktop/ruoyierp/ruoyi-erp-vue/src/assets/logo/logo.png')
print(im.size)
"
```

注意：源图是 JPG 无透明通道，若在深色侧边栏上白底突兀，需告知用户可能要一张透明底 PNG。同时删除 `gzh.jpg` 及其引用。
- [ ] **Step 4:** 执行昵称 SQL；刷新页面验证：浏览器标签、登录页、侧边栏 logo/标题、首页无广告
- [ ] **Step 5: Commit** `git commit -m "chore: 品牌化调整为智辉科技企业智能体"`

---

### Task 9: Langfuse 可观测（可选收尾）

**Files:**
- Modify: `ruoyi-modules/ruoyi-ai/pom.xml`（加 micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp + spring-boot-starter-actuator）
- Modify: `ruoyi-admin-erp/config/application-dev.yml`

- [ ] **Step 1:** Docker 启动 Langfuse：`docker run -d --name langfuse -p 3300:3000 -e DATABASE_URL=... langfuse/langfuse`（或官方 docker-compose），注册项目拿 public/secret key
- [ ] **Step 2:** pom 加依赖（版本由 Boot BOM 管理）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

- [ ] **Step 3:** 配置导出（Basic Auth 为 base64(pk:sk)）：

```yaml
management:
  tracing:
    sampling.probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:3300/api/public/otel/v1/traces
      headers:
        Authorization: "Basic <base64(pk-lf-xxx:sk-lf-xxx)>"
spring.ai:
  chat.observations.log-prompt: true
```

- [ ] **Step 4:** 重启后提问一次，在 Langfuse UI 看到 trace：用户问题 → 模型决策 → 工具调用（SQL 入参出参）→ 最终回答
- [ ] **Step 5: Commit** `git commit -m "feat(ai): 接入 Langfuse 可观测"`

---

## Self-Review 结果

- **Spec 覆盖**：模块骨架 ✓ 模型配置（sys_config + 前端弹窗）✓ text2SQL 双保险 ✓ 权限过滤 ✓ SSE 接口 ✓ 前端页面 ✓ 菜单权限 ✓ Langfuse ✓ 验收标准（Task 6 Step 2/3 正反验证）✓；品牌化与系统配置弹窗为用户追加需求（Task 7/8）
- **占位符**：DeepSeek Key 由用户在系统配置弹窗填写、Task 8 logo 文件、Task 9 Langfuse key 为用户必须提供的外部输入，已明确标注
- **类型一致性**：`SalesAnalysisTool.PERM` / `executeAnalysisQuery` / `chatClientBuilder` / component 路径 `ai/analysis/index` 前后一致
