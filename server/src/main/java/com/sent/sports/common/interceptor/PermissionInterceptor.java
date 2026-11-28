package com.sent.sports.common.interceptor;

import com.sent.sports.common.anno.PublicEndpoint;
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
 * <p>四条约定：
 * <ul>
 *   <li>方法上的注解优先于类上的注解（更具体的声明覆盖类级默认）；</li>
 *   <li>{@link PublicEndpoint} 最高优先：显式声明的公开端点直接放行；</li>
 *   <li><b>管理端默认拒绝</b>（2026-11-28 P0-01）：{@code /api/admin/**} 未标 {@code @RequirePerm} 时
 *       必须是管理员身份，匿名与客户令牌一律 {@code FORBIDDEN(40301)}。此前「未注解即放行」让 22 个
 *       管理端只读接口（订单/客户/退款/会话/库存）对匿名开放，属未授权访问；</li>
 *   <li>非管理端路径未注解则放行（公开目录、登录刷新等），保持既有契约。</li>
 * </ul>
 * 受保护端点上未登录访问抛 {@code UNAUTHENTICATED(40101)}，权限不足抛 {@code FORBIDDEN(40301)}——
 * 两者分开，前端才能区分「去登录」和「无权限」。管理端默认拒绝返回 403 而非 401：不对外区分
 * 「端点存在且需要登录」与「端点存在但你没权限」，避免匿名探测管理端结构。
 * <p>只依赖 {@link PermissionChecker} 窄端口，不感知角色/权限表结构，也不依赖 system 模块。
 */
public class PermissionInterceptor implements HandlerInterceptor {

    /** 管理端 API 前缀：未显式声明权限码时，默认要求管理员身份。 */
    private static final String ADMIN_API_PREFIX = "/api/admin";

    private final PermissionChecker permissionChecker;

    public PermissionInterceptor(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isPublicEndpoint(handlerMethod)) {
            return true;
        }
        RequirePerm required = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (required == null || required.value().length == 0) {
            // 未声明权限码：管理端默认拒绝（只认管理员身份），其余路径维持放行
            if (!isAdminApi(request)) {
                return true;
            }
            return requireAdmin(request);
        }
        CurrentUser currentUser = (CurrentUser) request.getAttribute(CurrentUser.ATTR);
        if (currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (!currentUser.isAdmin()) {
            // 客户侧没有 sys_role 权限矩阵：用户端（aud=user）的 @RequirePerm 是 P4 契约占位，
            // 实际约束由 AuthFilter 的登录态 + DataScope 的资源归属承担。若客户令牌访问
            // /api/admin/** 则直接拒绝，避免借这条规则越权访问管理端。
            if (!isAdminApi(request)) {
                return true;
            }
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        for (String permission : required.value()) {
            boolean allowed = required.exact()
                    ? permissionChecker.hasExplicit(currentUser.userId(), permission)
                    : permissionChecker.has(currentUser.userId(), permission);
            if (!allowed) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }
        return true;
    }

    /** 显式公开端点：方法级或类级 {@link PublicEndpoint} 任一命中即放行。 */
    private static boolean isPublicEndpoint(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PublicEndpoint.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicEndpoint.class);
    }

    /** 管理端 API 判定：/api/admin 与 /api/admin/** 都算，避免只差一个斜杠绕过默认拒绝。 */
    private static boolean isAdminApi(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.equals(ADMIN_API_PREFIX) || uri.startsWith(ADMIN_API_PREFIX + "/");
    }

    /** 管理端默认拒绝的唯一放行条件：管理员身份。 */
    private static boolean requireAdmin(HttpServletRequest request) {
        CurrentUser currentUser = (CurrentUser) request.getAttribute(CurrentUser.ATTR);
        if (currentUser == null || !currentUser.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
