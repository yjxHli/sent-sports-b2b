package com.sent.sports.customer;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.impl.CustomerOperationServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户运营命令真库实现单测（TDD 绿阶段）。
 * 覆盖缺陷根因：内存仓储查无客户导致的 404、非法等级码导致的 50001，
 * 以及条件更新 + 流水落账是否成对发生。
 */
class CustomerOperationServiceImplTest extends BaseUnitTest {

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerOperationServiceImpl service;

    @Captor
    private ArgumentCaptor<CustomerMapper.OperationLogRow> operationLogCaptor;

    @Captor
    private ArgumentCaptor<CustomerMapper.PointLogRow> pointLogCaptor;

    private static CustomerPO customer(String level, long points, Long owner) {
        CustomerPO po = new CustomerPO();
        po.setLevelCode(level);
        po.setPointsBalance(points);
        po.setOwnerStaffId(owner);
        po.setDeleted(0);
        return po;
    }

    @Test
    @DisplayName("改派：真库客户存在时更新归属并写操作流水，返回新旧归属")
    void transferUpdatesRealCustomerAndWritesLog() {
        when(customerMapper.selectById(91203L)).thenReturn(customer("LV1", 100L, 9004L));
        when(customerMapper.updateOwnerStaff(91203L, 9005L)).thenReturn(1);

        AdminCustomerDtos.TransferVO r = service.transfer(91203L, 9005L, "区域调整");

        assertEquals(91203L, r.customerId());
        assertEquals(9004L, r.fromStaffId());
        assertEquals(9005L, r.toStaffId());
        verify(customerMapper).updateOwnerStaff(91203L, 9005L);
        verify(customerMapper).insertOperationLog(operationLogCaptor.capture());
        CustomerMapper.OperationLogRow log = operationLogCaptor.getValue();
        assertEquals("TRANSFER", log.action());
        assertEquals("区域调整", log.reason());
        assertEquals("91203", log.targetId());
    }

    @Test
    @DisplayName("改派：客户不存在 / 软删时抛 NOT_FOUND，不写任何流水")
    void transferRejectsMissingCustomer() {
        when(customerMapper.selectById(1L)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.transfer(1L, 9005L, "调整"));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(customerMapper, never()).updateOwnerStaff(anyLong(), anyLong());
        verify(customerMapper, never()).insertOperationLog(any());
    }

    @Test
    @DisplayName("改派：原因空白 / 目标员工非法返回参数错误")
    void transferValidatesInput() {
        BizException blank = assertThrows(BizException.class,
                () -> service.transfer(91203L, 9005L, "  "));
        assertEquals(ErrorCode.PARAM_INVALID, blank.getErrorCode());

        BizException staff = assertThrows(BizException.class,
                () -> service.transfer(91203L, 0L, "调整"));
        assertEquals(ErrorCode.PARAM_INVALID, staff.getErrorCode());
        verify(customerMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("授级：旧前端的 GOLD 返回 40001 参数错误，而不是 50001 系统异常")
    void grantLevelRejectsInvalidLevelAsParamError() {
        BizException e = assertThrows(BizException.class,
                () -> service.grantLevel(91203L, "GOLD", 1893456000000L, "业绩达标"));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("等级码不合法：GOLD（可选 NORMAL / LV1 / LV2 / LV3）", e.resolvedMessage());
        verify(customerMapper, never()).updateLevelCode(anyLong(), any());
    }

    @Test
    @DisplayName("授级：合法等级码写回 customer.level_code 并记录有效期")
    void grantLevelUpdatesLevelCode() {
        when(customerMapper.selectById(91203L)).thenReturn(customer("LV1", 100L, 9004L));
        when(customerMapper.updateLevelCode(91203L, "LV2")).thenReturn(1);

        AdminCustomerDtos.LevelVO r = service.grantLevel(91203L, "LV2", 1893456000000L, "业绩达标");

        assertEquals("LV2", r.level());
        assertEquals(1893456000000L, r.validToMillis());
        verify(customerMapper).insertOperationLog(operationLogCaptor.capture());
        assertEquals("GRANT_LEVEL", operationLogCaptor.getValue().action());
    }

    @Test
    @DisplayName("积分调整：按增量更新并写 point_log 流水，负增量同样支持")
    void adjustPointsAddsDeltaAndWritesFlow() {
        when(customerMapper.selectById(91203L)).thenReturn(customer("LV1", 2427L, 9004L));
        when(customerMapper.addPoints(91203L, -20L)).thenReturn(1);

        AdminCustomerDtos.PointVO r = service.adjustPoints(
                91203L, -20L, "纠正发放", "ORDER", "SO-1");

        assertEquals(2427L, r.beforePoints());
        assertEquals(2407L, r.afterPoints());
        assertEquals(-20L, r.delta());
        verify(customerMapper).addPoints(91203L, -20L);
        verify(customerMapper).insertPointLog(pointLogCaptor.capture());
        CustomerMapper.PointLogRow log = pointLogCaptor.getValue();
        assertEquals(2407L, log.balanceAfter());
        assertEquals("ORDER", log.sourceType());
        assertEquals("SO-1", log.sourceId());
    }

    @Test
    @DisplayName("积分调整：0 增量与客户不存在都被拒绝，不落流水")
    void adjustPointsRejectsZeroDeltaAndMissingCustomer() {
        BizException zero = assertThrows(BizException.class,
                () -> service.adjustPoints(91203L, 0L, "调整", null, null));
        assertEquals(ErrorCode.PARAM_INVALID, zero.getErrorCode());

        when(customerMapper.selectById(404L)).thenReturn(null);
        BizException missing = assertThrows(BizException.class,
                () -> service.adjustPoints(404L, 1L, "调整", null, null));
        assertEquals(ErrorCode.NOT_FOUND, missing.getErrorCode());
        verify(customerMapper, never()).insertPointLog(any());
    }

    @Test
    @DisplayName("条件更新返回 0 行（并发删除）时抛 NOT_FOUND，不留下半截流水")
    void conditionalUpdateMissIsNotFound() {
        when(customerMapper.selectById(91203L)).thenReturn(customer("LV1", 100L, 9004L));
        when(customerMapper.updateOwnerStaff(91203L, 9005L)).thenReturn(0);

        BizException e = assertThrows(BizException.class,
                () -> service.transfer(91203L, 9005L, "调整"));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(customerMapper, never()).insertOperationLog(any());
    }
}
