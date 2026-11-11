package com.sent.sports.system.service;

import com.sent.sports.common.web.PermissionChecker;

import java.util.Set;

/**
 * 权限服务（P5-27）：在 {@link PermissionChecker} 之上补充「按员工取全部权限」与缓存失效能力。
 * <p>缓存口径：{@code perm:{staffId}} 级别的短 TTL（60s）缓存，权限变更时主动清理，
 * 既不追求强一致（不做分布式广播），也避免每次请求打库——与本项目「轻量防重、先跑通」的取向一致。
 */
public interface PermissionService extends PermissionChecker {

    /** 某员工的全部权限点（未分配角色时返回空集合）。 */
    Set<String> permissionsOf(long staffId);

    /** 员工角色或角色权限变更后清理该员工缓存。 */
    void evictStaff(long staffId);

    /** 角色权限整体变更（批量导入等）后清理全部缓存。 */
    void evictAll();
}
