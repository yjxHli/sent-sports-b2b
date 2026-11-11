package com.sent.sports.system.service.impl;

import com.sent.sports.system.mapper.PermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 权限服务单测（P5-27）：缓存命中、过期重载、无角色、失效清理。 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private PermissionMapper permissionMapper;

    private MutableClock clock;
    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-04T02:00:00Z"));
        service = new PermissionServiceImpl(permissionMapper, clock);
    }

    @Test
    @DisplayName("首次查库、二次命中缓存")
    void permissionsAreCached() {
        when(permissionMapper.selectRoleIdByStaffId(7L)).thenReturn(3L);
        when(permissionMapper.selectCodesByRoleId(3L)).thenReturn(List.of("order:ship", "finance:writeoff"));

        assertThat(service.permissionsOf(7L)).containsExactlyInAnyOrder("order:ship", "finance:writeoff");
        assertThat(service.permissionsOf(7L)).hasSize(2);

        verify(permissionMapper, times(1)).selectRoleIdByStaffId(7L);
        verify(permissionMapper, times(1)).selectCodesByRoleId(3L);
    }

    @Test
    @DisplayName("超过 60 秒缓存失效并重新加载")
    void cacheExpiresAfterTtl() {
        when(permissionMapper.selectRoleIdByStaffId(7L)).thenReturn(3L);
        when(permissionMapper.selectCodesByRoleId(3L)).thenReturn(List.of("order:ship"));

        service.permissionsOf(7L);
        clock.advance(Duration.ofSeconds(61));
        service.permissionsOf(7L);

        verify(permissionMapper, times(2)).selectRoleIdByStaffId(7L);
    }

    @Test
    @DisplayName("未分配角色返回空集合，且 has 一律 false")
    void staffWithoutRoleHasNoPermission() {
        when(permissionMapper.selectRoleIdByStaffId(9L)).thenReturn(null);

        assertThat(service.permissionsOf(9L)).isEmpty();
        assertThat(service.has(9L, "order:ship")).isFalse();
    }

    @Test
    @DisplayName("has 精确匹配权限点，空权限点视为无权限")
    void hasMatchesExactCode() {
        when(permissionMapper.selectRoleIdByStaffId(7L)).thenReturn(3L);
        when(permissionMapper.selectCodesByRoleId(3L)).thenReturn(List.of("order:ship"));

        assertThat(service.has(7L, "order:ship")).isTrue();
        assertThat(service.has(7L, "order:cancel")).isFalse();
        assertThat(service.has(7L, " ")).isFalse();
        assertThat(service.has(7L, null)).isFalse();
    }

    @Test
    @DisplayName("主动失效后重新查库")
    void evictForcesReload() {
        when(permissionMapper.selectRoleIdByStaffId(7L)).thenReturn(3L);
        when(permissionMapper.selectCodesByRoleId(3L)).thenReturn(List.of("order:ship"));

        service.permissionsOf(7L);
        service.evictStaff(7L);
        service.permissionsOf(7L);
        verify(permissionMapper, times(2)).selectRoleIdByStaffId(7L);

        service.evictAll();
        service.permissionsOf(7L);
        verify(permissionMapper, times(3)).selectRoleIdByStaffId(7L);
    }

    /** 可推进的测试时钟（TTL 断言不依赖真实等待）。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
