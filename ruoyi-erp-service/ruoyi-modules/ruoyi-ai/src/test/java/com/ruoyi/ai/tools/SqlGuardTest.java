package com.ruoyi.ai.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("local")
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
            () -> SqlGuard.assertSafeSelect("SELECT 1 -- "));
    }

    @Test
    void rejectsSysTables() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT password FROM sys_user"));
    }

    @Test
    void rejectsSchemaPrefixedSystemTable() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT * FROM sys.user"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT * FROM mysql.user"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT * FROM information_schema.tables"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT * FROM performance_schema.*"));
    }

    @Test
    void rejectsSchemaPrefixedWithSpaces() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT * FROM sys . user"));
    }

    @Test
    void rejectsBacktickWrappedSysTable() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT * FROM `sys_user`"));
    }

    @Test
    void rejectsMoreSysTables() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT password FROM sys_config"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("SELECT * FROM sys_role"));
    }

    @Test
    void allowsCteSelect() {
        assertDoesNotThrow(() -> SqlGuard.assertSafeSelect(
            "WITH s AS (SELECT 1) SELECT * FROM s"));
        assertDoesNotThrow(() -> SqlGuard.assertSafeSelect(
            "WITH sales AS (SELECT * FROM sales_order WHERE create_time >= '2026-07-01') SELECT * FROM sales"));
    }

    @Test
    void rejectsCteWithDml() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("WITH s AS (SELECT 1) DELETE FROM s"));
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("WITH s AS (DELETE FROM t) SELECT * FROM s"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect(null));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect(""));
    }

    @Test
    void rejectsBlankString() {
        assertThrows(IllegalArgumentException.class,
            () -> SqlGuard.assertSafeSelect("   "));
    }
}
