package com.sent.sports.coupon.service;

import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 发放记录批量删除：只打 customer_coupon.deleted，不改订单折扣与券状态。 */
class CouponIssueAdminUseCaseTest extends BaseUnitTest {

    @Mock
    private CouponMapper couponMapper;

    @InjectMocks
    private CouponIssueAdminUseCase useCase;

    @Test
    void deleteNormalizesIdsLikeConfirmToken() {
        when(couponMapper.batchMarkDeleted(List.of("101", "102"))).thenReturn(2);

        assertEquals(2, useCase.delete(List.of("102", " 101 ", "102")));

        // trim → 去空 → 去重 → 字典序，才能与前端换到的 X-Confirm-Token 对齐
        verify(couponMapper).batchMarkDeleted(List.of("101", "102"));
    }
}
