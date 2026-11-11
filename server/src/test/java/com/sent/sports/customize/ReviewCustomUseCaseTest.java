package com.sent.sports.customize;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomReviewLog;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.ReviewCustomUseCase;
import com.sent.sports.customize.service.dto.ReviewCustomCmd;
import com.sent.sports.customize.service.dto.ReviewCustomResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核定制单用例测试（P3-26，TDD 红）：覆盖 spec §6.5.3 / A5/A6——
 * 通过锁定图稿版本、驳回原因必填(分类+说明)、驳回次数+1、非待审核不可审核(含 ESCALATED 客服不可再审)、
 * 评审时计算 24h 审核时限剩余。此时 ReviewCustomUseCase 等尚未实现，编译即失败（红）。
 */
class ReviewCustomUseCaseTest extends BaseUseCaseTest {

    private static final long DEADLINE = 2_000L;
    private static final Instant NOW = Instant.ofEpochMilli(1_000L);

    @Mock
    private CustomizeRepository customizeRepository;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private ReviewCustomUseCase useCase;
    @Captor
    private ArgumentCaptor<CustomOrder> orderCaptor;
    @Captor
    private ArgumentCaptor<CustomReviewLog> logCaptor;

    private CustomOrder waitingReview(int version, int rejectCount) {
        return new CustomOrder("CO20240101000001", CustomStatus.WAIT_REVIEW, version, rejectCount, DEADLINE);
    }

    @Test
    void passLocksVersionAndApproves() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(waitingReview(1, 0)));
        when(clock.instant()).thenReturn(NOW);

        ReviewCustomResult r = useCase.review(new ReviewCustomCmd("CO20240101000001", "PASS", null, null));

        verify(customizeRepository).save(orderCaptor.capture());
        assertEquals(CustomStatus.APPROVED, orderCaptor.getValue().getStatus());
        assertEquals(1, orderCaptor.getValue().getLockedVersion());
        assertEquals(CustomStatus.APPROVED.name(), r.status());
        assertEquals(1, r.lockedVersion());
        assertEquals(DEADLINE - NOW.toEpochMilli(), r.remainingMillis());
    }

    @Test
    void rejectRequiresReasonCodeAndRemark() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(waitingReview(1, 0)));

        BizException e = assertThrows(BizException.class,
                () -> useCase.review(new ReviewCustomCmd("CO20240101000001", "REJECT", null, null)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }

    @Test
    void rejectMovesToRejectedAndIncrementsRejectCount() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(waitingReview(1, 1)));
        when(clock.instant()).thenReturn(NOW);

        ReviewCustomResult r = useCase.review(new ReviewCustomCmd("CO20240101000001", "REJECT", "LOW_RES", "分辨率不足"));

        verify(customizeRepository).save(orderCaptor.capture());
        assertEquals(CustomStatus.REJECTED, orderCaptor.getValue().getStatus());
        assertEquals(2, orderCaptor.getValue().getRejectCount());
        assertEquals(CustomStatus.REJECTED.name(), r.status());
    }

    @Test
    void reviewWritesAuditLogWithReviewedVersionAndReviewer() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(waitingReview(2, 1)));
        when(clock.instant()).thenReturn(NOW);

        useCase.review(new ReviewCustomCmd("CO20240101000001", "PASS", null, null, 9003L));

        verify(customizeRepository).insertReviewLog(logCaptor.capture());
        CustomReviewLog log = logCaptor.getValue();
        assertEquals("CO20240101000001", log.getCustomOrderId());
        assertEquals(2, log.getDraftVersion());
        assertEquals("PASS", log.getResult());
        assertEquals(9003L, log.getReviewerId());
        assertEquals(NOW.toEpochMilli(), log.getCreatedAtMillis());
    }

    @Test
    void cannotReviewWhenNotWaitingReview() {
        // ESCALATED（第 4 次转主管后）客服不可再审；亦覆盖 APPROVED/REJECTED 等非法状态
        when(customizeRepository.findByCustomOrderId("CO20240101000001"))
                .thenReturn(Optional.of(new CustomOrder("CO20240101000001", CustomStatus.ESCALATED, 1, 3, DEADLINE)));

        BizException e = assertThrows(BizException.class,
                () -> useCase.review(new ReviewCustomCmd("CO20240101000001", "PASS", null, null)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
    }
}
