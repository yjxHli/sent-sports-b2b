package com.sent.sports.common.web;

/**
 * 权限判定端口（P5-27）。
 * <p>为什么放在 common 且只有两个方法：{@code common} 不允许依赖任何业务模块（ArchUnit 规则 3），
 * 而拦截器又必须问「这个员工有没有这个权限点」，因此把问题抽成最窄的端口，
 * 由 system 模块的 {@code PermissionService} 实现（含 60s 缓存），拦截器只依赖本接口。
 */
public interface PermissionChecker {

    /** 员工是否拥有该权限点（权限点形如 {@code order:ship}）。 */
    boolean has(long staffId, String permission);
}
