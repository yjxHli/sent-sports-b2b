package com.sent.sports.system.service.impl;

import com.sent.sports.system.mapper.PermissionMapper;
import com.sent.sports.system.service.PermissionService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限服务实现（P5-27）：员工 → 角色 → 权限码，结果按员工缓存 60 秒。
 * <p>缓存是「本地内存 + TTL」而非 Redis：权限数据量小、变更频率低，单机缓存已足够；
 * 变更时调用 {@link #evictStaff(long)} 主动失效，多实例场景下最多容忍 60 秒延迟（可接受，不做广播）。
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    static final Duration TTL = Duration.ofSeconds(60);

    private final PermissionMapper permissionMapper;
    private final Clock clock;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public PermissionServiceImpl(PermissionMapper permissionMapper, Clock clock) {
        this.permissionMapper = permissionMapper;
        this.clock = clock;
    }

    @Override
    public Set<String> permissionsOf(long staffId) {
        long now = clock.instant().toEpochMilli();
        Cached cached = cache.get(staffId);
        if (cached != null && cached.expireAtMillis() > now) {
            return cached.permissions();
        }
        Set<String> permissions = load(staffId);
        cache.put(staffId, new Cached(permissions, now + TTL.toMillis()));
        return permissions;
    }

    @Override
    public boolean has(long staffId, String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        Set<String> owned = permissionsOf(staffId);
        // 与前端 permission store 的 has() 口径一致：持有 '*' 即超级管理员，放行全部权限点。
        return owned.contains("*") || owned.contains(permission);
    }

    @Override
    public void evictStaff(long staffId) {
        cache.remove(staffId);
    }

    @Override
    public void evictAll() {
        cache.clear();
    }

    private Set<String> load(long staffId) {
        Long roleId = permissionMapper.selectRoleIdByStaffId(staffId);
        if (roleId == null) {
            return Set.of();
        }
        List<String> codes = permissionMapper.selectCodesByRoleId(roleId);
        return codes == null ? Set.of() : Set.copyOf(codes);
    }

    private record Cached(Set<String> permissions, long expireAtMillis) {
    }
}
