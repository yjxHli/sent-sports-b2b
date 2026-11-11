package com.sent.sports.common.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注解（P4-02）：标注在 Controller 方法或类上，声明该端点所需的权限码。
 * 例如 {@code @RequirePerm("order:ship")}。
 * 实际校验由 PermissionInterceptor（Phase 5）在请求进入时执行：
 * 取出当前 {@code CurrentUser} 的权限集合，命中全部声明码才放行，否则 {@code 40301}。
 * 可标注多个码，默认需全部满足（AND）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    /** 权限码，如 "order:ship"、"stock:inbound" */
    String[] value();
}
