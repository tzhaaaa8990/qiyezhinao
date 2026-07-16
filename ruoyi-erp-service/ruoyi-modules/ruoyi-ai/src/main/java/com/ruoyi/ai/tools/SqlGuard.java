package com.ruoyi.ai.tools;

import java.util.regex.Pattern;

/**
 * AI 生成 SQL 的应用层守卫：仅允许单条 SELECT，禁注释/多语句/系统表。
 * 数据层另有只读账号兜底。
 */
public final class SqlGuard {

    private static final Pattern FORBIDDEN = Pattern.compile(
        "(;|--|/\\*|#)|\\b(update|delete|insert|drop|alter|truncate|grant|create|replace|call|load|outfile)\\b|"
        + "\\bsys_\\w+|\\b(sys|mysql|information_schema|performance_schema)\\s*\\.",
        Pattern.CASE_INSENSITIVE);

    private SqlGuard() {
    }

    public static void assertSafeSelect(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        if (!trimmed.toLowerCase().startsWith("select") && !trimmed.toLowerCase().startsWith("with")) {
            throw new IllegalArgumentException("仅允许 SELECT 查询");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("SQL 含禁止的关键字/注释/多语句/系统表");
        }
    }
}
