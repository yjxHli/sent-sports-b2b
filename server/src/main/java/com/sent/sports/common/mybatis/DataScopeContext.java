package com.sent.sports.common.mybatis;

import java.util.List;
import java.util.Optional;

/**
 * 数据范围上下文（P5-28）：把「当前员工能看到谁的数据」从 Web 层传到 MyBatis 拦截器。
 * <p>拦截器运行在业务线程里，无法直接拿 HttpServletRequest，因此在请求入口
 * （{@code DataScopeWebInterceptor}）解析一次并绑定到 ThreadLocal，请求结束必须 {@link #clear()}。
 * 不设置上下文时不改写 SQL（定时任务、内部批处理等场景保持全量可见）。
 */
public final class DataScopeContext {

    private static final ThreadLocal<Scope> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(Scope scope) {
        HOLDER.set(scope);
    }

    public static Optional<Scope> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 范围快照：级别 + 当前员工 + 可见员工 ID 列表（ALL 时为空列表表示不限制）。 */
    public record Scope(String level, long staffId, List<Long> visibleStaffIds) {

        public static final String ALL = "ALL";
        public static final String TEAM = "TEAM";
        public static final String SELF = "SELF";

        public static Scope all(long staffId) {
            return new Scope(ALL, staffId, List.of());
        }

        public static Scope self(long staffId) {
            return new Scope(SELF, staffId, List.of(staffId));
        }

        public static Scope team(long staffId, List<Long> visibleStaffIds) {
            return new Scope(TEAM, staffId, List.copyOf(visibleStaffIds));
        }

        public boolean isAll() {
            return ALL.equalsIgnoreCase(level());
        }
    }
}
