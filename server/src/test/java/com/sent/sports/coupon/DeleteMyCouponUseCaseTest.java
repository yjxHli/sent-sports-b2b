package com.sent.sports.coupon;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.DeleteMyCouponUseCase;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 用户删除自己的券：只用完/过期可删，越权按不存在处理，重复删除幂等。 */
class DeleteMyCouponUseCaseTest extends BaseUnitTest {

    @Mock
    private CouponMapper couponMapper;

    private DeleteMyCouponUseCase useCase;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        useCase = new DeleteMyCouponUseCase(couponMapper, fixedClock());
        now = LocalDateTime.now(fixedClock());
    }

    @Test
    void unauthenticatedIsRejectedBeforeAnyQuery() {
        BizException ex = assertThrows(BizException.class, () -> useCase.delete(42L, 0L));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verifyNoInteractions(couponMapper);
    }

    @Test
    void couponOfAnotherCustomerIsNotFound() {
        when(couponMapper.selectForOwnerCheck(42L)).thenReturn(coupon(42L, 9003L, "USED", true, false));

        BizException ex = assertThrows(BizException.class, () -> useCase.delete(42L, 9002L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(couponMapper, never()).softDeleteMine(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingCouponIsNotFound() {
        BizException ex = assertThrows(BizException.class, () -> useCase.delete(42L, 9002L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void unusedCouponCannotBeDeleted() {
        when(couponMapper.selectForOwnerCheck(42L)).thenReturn(coupon(42L, 9002L, "UNUSED", true, false));

        BizException ex = assertThrows(BizException.class, () -> useCase.delete(42L, 9002L));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("已使用") || ex.resolvedMessage().contains("已过期"));
        verify(couponMapper, never()).softDeleteMine(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usedCouponCanBeDeleted() {
        when(couponMapper.selectForOwnerCheck(42L)).thenReturn(coupon(42L, 9002L, "USED", true, false));
        when(couponMapper.softDeleteMine(42L, 9002L, now)).thenReturn(1);

        useCase.delete(42L, 9002L);

        verify(couponMapper).softDeleteMine(42L, 9002L, now);
    }

    @Test
    void expiredCouponCanBeDeleted() {
        when(couponMapper.selectForOwnerCheck(42L)).thenReturn(coupon(42L, 9002L, "UNUSED", false, false));
        when(couponMapper.softDeleteMine(42L, 9002L, now)).thenReturn(1);

        useCase.delete(42L, 9002L);

        verify(couponMapper).softDeleteMine(42L, 9002L, now);
    }

    @Test
    void alreadyDeletedCouponIsIdempotent() {
        when(couponMapper.selectForOwnerCheck(42L)).thenReturn(coupon(42L, 9002L, "USED", true, true));

        assertDoesNotThrow(() -> useCase.delete(42L, 9002L));

        verify(couponMapper, never()).softDeleteMine(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void racingDeleteAffectingZeroRowsStillSucceeds() {
        when(couponMapper.selectForOwnerCheck(42L)).thenReturn(coupon(42L, 9002L, "USED", true, false));
        when(couponMapper.softDeleteMine(42L, 9002L, now)).thenReturn(0);

        assertDoesNotThrow(() -> useCase.delete(42L, 9002L));
    }

    private CustomerCouponPO coupon(long id, long customerId, String status,
                                    boolean futureExpiry, boolean deleted) {
        CustomerCouponPO po = new CustomerCouponPO();
        po.setId(id);
        po.setCustomerId(customerId);
        po.setStatus(status);
        po.setExpireAt(futureExpiry ? now.plusDays(30) : now.minusDays(1));
        po.setDeleted(deleted ? 1 : 0);
        return po;
    }
}
