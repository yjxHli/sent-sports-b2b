package com.sent.sports.common.interceptor;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PermissionChecker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 权限拦截器（P5-27）：读取 {@link RequirePerm}，逐个校验当前员工是否持有对应权限点。
 * <p>三条约定：
 * <ul>
 *   <li>方法上的注解优先于类上的注解（更具体的声明覆盖类级默认）；</li>
 *   <li>未标注注解的处理器直接放行（公开接口、登录刷新等）；</li>
 *   <li>未登录访问受保护接口抛 {@code UNAUTHENTICATED(40101)}，权限不足抛 {@code FORBIDDEN(40301)}——
 *       两者分开，前端才能区分「去登录」和「无权限」。</li>
 * </ul>
 * 只依赖 {@link PermissionChecker} 窄端口，不感知角色/权限表结构，也不依赖 system 模块。
 */
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;

    public PermissionInterceptor(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePerm required = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (required == null || required.value().length == 0) {
            return true;
        }
        CurrentUser currentUser = (CurrentUser) request.getAttribute(CurrentUser.ATTR);
        if (currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (!currentUser.isAdmin()) {
            // 客户侧没有 sys_role 权限矩阵：用户端（aud=user）的 @RequirePerm 是 P4 契约占位，
            // 实际约束由 AuthFilter 的登录态 + DataScope 的资源归属承担。若客户令牌访问
            // /api/admin/** 则直接拒绝，避免借这条规则越权访问管理端。
            if (!request.getRequestURI().startsWith("/api/admin/")) {
                return true;
            }
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        for (String permission : required.value()) {
            if (!permissionChecker.has(currentUser.userId(), permission)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }
        return true;
    }
}
