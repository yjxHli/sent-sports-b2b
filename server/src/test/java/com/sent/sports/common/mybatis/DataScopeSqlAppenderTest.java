package com.sent.sports.common.mybatis;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 数据范围 SQL 追加器单测（P5-28）：括号优先级、尾部子句、字面量/注释/子查询、注入防护。 */
class DataScopeSqlAppenderTest {

    private static final String COND = "o.owner_staff_id = 7";

    @Test
    void appendsToExistingWhereWithoutBreakingOr() {
        String sql = "SELECT * FROM orders o WHERE o.status = 'WAIT_PAY' OR o.status = 'PAID' ORDER BY o.id";

        String result = DataScopeSqlAppender.append(sql, COND);

        assertTrue(result.startsWith("SELECT * FROM orders o WHERE (" + COND + ") AND ("));
        assertTrue(result.contains("AND (o.status = 'WAIT_PAY' OR o.status = 'PAID')"));
        assertTrue(result.endsWith("ORDER BY o.id"), "ORDER BY 必须留在括号外");
        assertEquals(-1, result.indexOf(") AND ()"));
    }

    @Test
    void insertsWhereBeforeOrderByWhenAbsent() {
        String sql = "SELECT o.* FROM orders o ORDER BY o.id DESC LIMIT 10";

        String result = DataScopeSqlAppender.append(sql, COND);

        assertEquals("SELECT o.* FROM orders o WHERE " + COND + " ORDER BY o.id DESC LIMIT 10", result);
    }

    @Test
    void insertsWhereAtEndWhenNoTailClause() {
        assertEquals("SELECT * FROM orders o WHERE " + COND,
                DataScopeSqlAppender.append("SELECT * FROM orders o", COND));
    }

    @Test
    void insertsBeforeGroupByAndHaving() {
        String result = DataScopeSqlAppender.append(
                "SELECT o.owner_staff_id, COUNT(*) c FROM orders o GROUP BY o.owner_staff_id HAVING c > 1", COND);

        assertTrue(result.contains("WHERE " + COND + " GROUP BY"));
    }

    @Test
    void ignoresKeywordsInsideQuotedLiteral() {
        String sql = "SELECT * FROM orders o WHERE o.remark = 'where order by group' AND o.id > 0";

        String result = DataScopeSqlAppender.append(sql, COND);

        assertTrue(result.contains("(" + COND + ") AND (o.remark = 'where order by group' AND o.id > 0)"));
    }

    @Test
    void ignoresComments() {
        String sql = "SELECT * FROM orders o /* where order by */ WHERE o.id > 0";

        String result = DataScopeSqlAppender.append(sql, COND);

        assertTrue(result.startsWith("SELECT * FROM orders o /* where order by */ WHERE (" + COND + ")"));
    }

    @Test
    void onlyRewritesOuterWhereOfSubquery() {
        String sql = "SELECT * FROM (SELECT * FROM orders WHERE status = 'X') t WHERE t.id > 0";

        String result = DataScopeSqlAppender.append(sql, COND);

        assertTrue(result.contains("FROM (SELECT * FROM orders WHERE status = 'X') t WHERE (" + COND + ")"));
        assertTrue(result.contains("AND (t.id > 0)"));
    }

    @Test
    void nonSelectIsUntouched() {
        String sql = "UPDATE orders SET status = 'CANCELLED' WHERE id = 1";
        assertEquals(sql, DataScopeSqlAppender.append(sql, COND));
    }

    @Test
    void blankConditionIsNoOp() {
        String sql = "SELECT * FROM orders o";
        assertEquals(sql, DataScopeSqlAppender.append(sql, "  "));
    }

    @Test
    void unionIsRejectedInsteadOfSilentlyLeakingRows() {
        String sql = "SELECT * FROM orders o WHERE o.id > 0 UNION SELECT * FROM orders_archive o";

        BizException e = assertThrows(BizException.class, () -> DataScopeSqlAppender.append(sql, COND));
        assertEquals(ErrorCode.BIZ_REJECT, e.getErrorCode());
    }

    @Test
    void identifierWhitelistBlocksInjection() {
        assertEquals("o.owner_staff_id", DataScopeSqlAppender.qualified("o", "owner_staff_id"));
        assertEquals("owner_staff_id", DataScopeSqlAppender.qualified("", "owner_staff_id"));
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class,
                        () -> DataScopeSqlAppender.qualified("o; DROP TABLE orders", "id")).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class,
                        () -> DataScopeSqlAppender.qualified("o", "1=1 OR x")).getErrorCode());
        assertFalse(DataScopeSqlAppender.qualified("o", "owner_staff_id").contains(";"));
    }
}
