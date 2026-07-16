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
