package com.sent.sports.common.mybatis;

import com.sent.sports.common.anno.DataScope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 数据范围 MyBatis 拦截器单测（P5-28）：ALL / TEAM / SELF 三类条件与 SQL 改写结果。 */
class DataScopeInterceptorTest {

    private final DataScopeInterceptor interceptor = new DataScopeInterceptor();

    private static final String SQL = "SELECT o.* FROM orders o WHERE o.status = 'WAIT_PAY' ORDER BY o.id";

    @Test
    void allLeavesSqlUntouched() {
        assertEquals(SQL, interceptor.rewrite(SQL, annotation(), DataScopeContext.Scope.all(7L)));
    }

    @Test
    void selfAppendsEquality() {
        String result = interceptor.rewrite(SQL, annotation(), DataScopeContext.Scope.self(7L));

        assertEquals("o.owner_staff_id = 7", interceptor.condition(annotation(), DataScopeContext.Scope.self(7L)));
        assertTrue(result.contains("WHERE (o.owner_staff_id = 7) AND (o.status = 'WAIT_PAY')"));
        assertTrue(result.endsWith("ORDER BY o.id"));
    }

    @Test
    void teamAppendsInList() {
        DataScopeContext.Scope scope = DataScopeContext.Scope.team(7L, List.of(7L, 8L, 9L));

        assertEquals("o.owner_staff_id IN (7,8,9)", interceptor.condition(annotation(), scope));
        assertTrue(interceptor.rewrite(SQL, annotation(), scope).contains("IN (7,8,9)"));
    }

    @Test
    void teamWithNoSubordinatesDeniesEverything() {
        DataScopeContext.Scope scope = DataScopeContext.Scope.team(7L, List.of());

        assertEquals("1 = 0", interceptor.condition(annotation(), scope));
    }

    @Test
    void unknownLevelFallsBackToSelf() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope("WHATEVER", 7L, List.of(7L, 8L));

        assertEquals("o.owner_staff_id = 7", interceptor.condition(annotation(), scope));
    }

    @Test
    void columnWithoutAlias() throws Exception {
        DataScope noAlias = annotation("noAlias");

        assertEquals("owner_staff_id = 7", interceptor.condition(noAlias, DataScopeContext.Scope.self(7L)));
    }

    @Test
    void nullScopeOrAnnotationIsNoOp() {
        assertEquals(SQL, interceptor.rewrite(SQL, null, DataScopeContext.Scope.self(7L)));
        assertEquals(SQL, interceptor.rewrite(SQL, annotation(), null));
    }

    private static DataScope annotation() {
        return annotation("sample");
    }

    private static DataScope annotation(String methodName) {
        try {
            Method method = DataScopeInterceptorTest.class.getDeclaredMethod(methodName);
            return method.getAnnotation(DataScope.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    @DataScope(alias = "o", column = "owner_staff_id")
    void sample() {
        // 仅用于提供注解实例
    }

    @DataScope
    void noAlias() {
        // 仅用于提供注解实例
    }
}
