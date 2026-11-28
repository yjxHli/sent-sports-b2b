package com.sent.sports.common.mybatis;

import com.sent.sports.common.anno.DataScope;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据范围 MyBatis 拦截器（P5-28 / plan §7.9）：对标注 {@link DataScope} 的 Mapper 查询改写 SQL。
 * <p>三类范围：
 * <ul>
 *   <li>{@code ALL}：不改写；</li>
 *   <li>{@code TEAM}：{@code alias.column IN (自己 + 直接下属)}，列表为空时降级为 {@code 1 = 0}；</li>
 *   <li>{@code SELF}（含未知级别的保守回落）：{@code alias.column = 自己}。</li>
 * </ul>
 * <p>只依赖本模块（common）的注解/上下文与 MyBatis API，不感知角色表或业务模块，
 * 角色范围的解析通过 {@code DataScopeResolver} 端口由 system 模块提供，符合 ArchUnit 规则 3/4。
 */
@Component
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
        args = {Connection.class, Integer.class}))
public class DataScopeInterceptor implements Interceptor {

    /** MyBatis-Plus 自动 count 语句的 id 后缀（见 {@code PaginationInnerInterceptor}）。 */
    private static final String MP_COUNT_SUFFIX = "_mpCount";

    private final Map<String, Optional<DataScope>> annotationCache = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject meta = SystemMetaObject.forObject(handler);
        MappedStatement mappedStatement = mappedStatement(meta);
        if (mappedStatement == null) {
            return invocation.proceed();
        }
        Optional<DataScope> annotation = annotationCache.computeIfAbsent(
                mappedStatement.getId(), this::findAnnotation);
        Optional<DataScopeContext.Scope> scope = DataScopeContext.current();
        if (annotation.isEmpty() || scope.isEmpty()) {
            return invocation.proceed();
        }
        BoundSql boundSql = handler.getBoundSql();
        String rewritten = rewrite(boundSql.getSql(), annotation.get(), scope.get());
        if (!rewritten.equals(boundSql.getSql())) {
            setSql(boundSql, rewritten);
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * 按范围改写 SQL（纯函数，便于单测）。ALL / 无有效条件时原样返回。
     */
    public String rewrite(String sql, DataScope annotation, DataScopeContext.Scope scope) {
        if (scope == null || annotation == null || scope.isAll()) {
            return sql;
        }
        return DataScopeSqlAppender.append(sql, condition(annotation, scope));
    }

    /** 生成完整过滤条件，标识符与员工 ID 均为白名单/数值，不含外部输入。 */
    String condition(DataScope annotation, DataScopeContext.Scope scope) {
        String column = DataScopeSqlAppender.qualified(annotation.alias(), annotation.column());
        if (DataScopeContext.Scope.TEAM.equalsIgnoreCase(scope.level())) {
            List<Long> ids = scope.visibleStaffIds();
            if (ids == null || ids.isEmpty()) {
                return "1 = 0";
            }
            return column + " IN (" + ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
        }
        return column + " = " + scope.staffId();
    }

    private MappedStatement mappedStatement(MetaObject meta) {
        if (meta.hasGetter("delegate.mappedStatement")) {
            return (MappedStatement) meta.getValue("delegate.mappedStatement");
        }
        if (meta.hasGetter("mappedStatement")) {
            return (MappedStatement) meta.getValue("mappedStatement");
        }
        return null;
    }

    private Optional<DataScope> findAnnotation(String mappedStatementId) {
        // MyBatis-Plus 分页会自动生成一条 count 语句，id 为「原方法名 + _mpCount」。
        // 不剥掉后缀就查不到注解 → 列表被过滤、total 却是全量，前端分页会出现「共 N 条但只有几行」。
        // 2026-11-28 批次 2 / P1-04：真实列表（订单/客户分页）接入数据范围时补齐这一点。
        String statementId = mappedStatementId.endsWith(MP_COUNT_SUFFIX)
                ? mappedStatementId.substring(0, mappedStatementId.length() - MP_COUNT_SUFFIX.length())
                : mappedStatementId;
        int dot = statementId.lastIndexOf('.');
        if (dot <= 0 || dot == statementId.length() - 1) {
            return Optional.empty();
        }
        try {
            Class<?> mapperType = Class.forName(statementId.substring(0, dot));
            String methodName = statementId.substring(dot + 1);
            for (Method method : mapperType.getMethods()) {
                if (method.getName().equals(methodName)) {
                    return Optional.ofNullable(method.getAnnotation(DataScope.class));
                }
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // 非标准 MappedStatement id（如动态生成）：跳过，不改写
        }
        return Optional.empty();
    }

    /**
     * {@code BoundSql.sql} 是 final 且无 setter，MyBatis MetaObject 不会为 final 字段生成 setMethod，
     * 因此用反射直接写回（MyBatis 生态中数据权限插件的通用做法）。
     */
    private void setSql(BoundSql boundSql, String sql) {
        try {
            Field field = BoundSql.class.getDeclaredField("sql");
            field.setAccessible(true);
            field.set(boundSql, sql);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("数据范围 SQL 改写失败", e);
        }
    }
}
