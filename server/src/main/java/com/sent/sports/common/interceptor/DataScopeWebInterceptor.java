package com.sent.sports.common.interceptor;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.DataScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 数据范围 Web 入口（P5-28）：在请求进入 Controller 前把当前员工的数据范围绑定到线程，
 * 由 {@code DataScopeInterceptor}（MyBatis 插件）在 SQL 执行时消费；请求结束无条件清理 ThreadLocal。
 * <p>user 端不参与数据范围（客户只看自己的数据，由业务查询条件约束），仅 admin 端生效。
 */
public class DataScopeWebInterceptor implements HandlerInterceptor {

    private final DataScopeResolver resolver;

    public DataScopeWebInterceptor(DataScopeResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object attribute = request.getAttribute(CurrentUser.ATTR);
        if (attribute instanceof CurrentUser currentUser && currentUser.isAdmin() && currentUser.userId() > 0) {
            resolver.resolve(currentUser.userId()).ifPresent(DataScopeContext::set);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        DataScopeContext.clear();
    }
}
