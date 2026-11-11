package com.sent.sports.system.service.impl;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.system.mapper.StaffMapper;
import com.sent.sports.system.mapper.po.SysRolePO;
import com.sent.sports.system.mapper.po.SysStaffPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 数据范围解析实现单测（P5-28）：ALL / TEAM / SELF 与「查不到角色」的保守回落。 */
@ExtendWith(MockitoExtension.class)
class StaffDataScopeResolverImplTest {

    @Mock
    private StaffMapper staffMapper;
    @InjectMocks
    private StaffDataScopeResolverImpl resolver;

    @Test
    void allHasNoRestriction() {
        when(staffMapper.selectRoleByStaffId(7L)).thenReturn(role("ALL"));

        DataScopeContext.Scope scope = resolver.resolve(7L).orElseThrow();

        assertTrue(scope.isAll());
        assertTrue(scope.visibleStaffIds().isEmpty());
        verify(staffMapper, never()).selectByDataScope("TEAM", 7L);
    }

    @Test
    void teamIncludesSelfAndDirectReports() {
        when(staffMapper.selectRoleByStaffId(7L)).thenReturn(role("TEAM"));
        when(staffMapper.selectByDataScope("TEAM", 7L)).thenReturn(List.of(staff(7L), staff(8L), staff(9L)));

        DataScopeContext.Scope scope = resolver.resolve(7L).orElseThrow();

        assertEquals(DataScopeContext.Scope.TEAM, scope.level());
        assertEquals(List.of(7L, 8L, 9L), scope.visibleStaffIds());
    }

    @Test
    void selfOnlySeesOwnId() {
        when(staffMapper.selectRoleByStaffId(7L)).thenReturn(role("SELF"));

        DataScopeContext.Scope scope = resolver.resolve(7L).orElseThrow();

        assertEquals(DataScopeContext.Scope.SELF, scope.level());
        assertEquals(List.of(7L), scope.visibleStaffIds());
    }

    @Test
    void missingRoleFallsBackToSelf() {
        when(staffMapper.selectRoleByStaffId(7L)).thenReturn(null);

        assertEquals(List.of(7L), resolver.resolve(7L).orElseThrow().visibleStaffIds());
    }

    @Test
    void unknownLevelFallsBackToSelf() {
        when(staffMapper.selectRoleByStaffId(7L)).thenReturn(role("ROOT_OF_EVERYTHING"));

        assertEquals(DataScopeContext.Scope.SELF, resolver.resolve(7L).orElseThrow().level());
    }

    private static SysRolePO role(String scope) {
        SysRolePO po = new SysRolePO();
        po.setDataScope(scope);
        return po;
    }

    private static SysStaffPO staff(long id) {
        SysStaffPO po = new SysStaffPO();
        po.setId(id);
        return po;
    }
}
