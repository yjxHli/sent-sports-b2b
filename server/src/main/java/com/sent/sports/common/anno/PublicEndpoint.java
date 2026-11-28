package com.sent.sports.common.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 公开端点注解（2026-11-28 批次 1 / P0-01）：显式声明「这个接口不需要登录，也不受数据权限约束」。
 *
 * <p><b>为什么需要它</b>：{@code /api/admin/**} 已改为「未标 {@link RequirePerm} 即默认要求管理员身份」，
 * 忘记注解会 403 而不是静默放行。个别确实要公开的端点（健康检查、公开字典等）必须显式豁免，
 * 让「默认拒绝、显式放行」在代码里可审计。</p>
 *
 * <p><b>使用边界</b>：只加在「无任何用户上下文」的端点上。需要登录但不需要具体权限码的接口，
 * 不要用它——那会把「登录门槛」也一并取消。要表达「登录即可」，用
 * {@code @RequirePerm("xxx:read")}（用户端令牌在 {@code PermissionInterceptor} 里不做权限码匹配，
 * 只校验登录态）。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
