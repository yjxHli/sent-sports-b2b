package com.sent.sports.customize;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customize.domain.CustomDraft;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.SubmitDraftUseCase;
import com.sent.sports.customize.service.dto.SubmitDraftCmd;
import com.sent.sports.customize.service.dto.SubmitDraftResult;
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
 * 提交图稿用例测试（P3-24，TDD 红）：覆盖 plan §7 / spec §6.5.3——
 * 版本号递增、必须有附件、单文件超 50MB 拒绝、驳回后重传(V1→V2 且回到待审核)、定制单不存在拒绝。
 * 此时 SubmitDraftUseCase / CustomOrder / CustomDraft 等尚未实现，编译即失败（红）。
 */
class SubmitDraftUseCaseTest extends BaseUseCaseTest {

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");
    private static final long MB = 1024L * 1024L;

    @Mock
    private CustomizeRepository customizeRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private SubmitDraftUseCase useCase;
    @Captor
    private ArgumentCaptor<CustomOrder> orderCaptor;
    @Captor
    private ArgumentCaptor<CustomDraft> draftCaptor;

    private CustomOrder waitingDraft() {
        return new CustomOrder("CO20240101000001", CustomStatus.WAIT_DRAFT, 0, 0, 0L);
    }

    private CustomOrder rejected() {
        return new CustomOrder("CO20240101000001", CustomStatus.REJECTED, 1, 1, 0L);
    }

    @Test
    void versionIncrementsAndMovesToWaitReview() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(waitingDraft()));
        when(clock.instant()).thenReturn(FIXED);
        when(idGen.next("DR")).thenReturn("DR20240101000001");

        SubmitDraftResult r = useCase.submit(new SubmitDraftCmd("CO20240101000001", "F1", "logo.png", 2 * MB));

        verify(customizeRepository).save(orderCaptor.capture());
        verify(customizeRepository).insertDraft(draftCaptor.capture());

        assertEquals(1, orderCaptor.getValue().getCurrentVersion());
        assertEquals(CustomStatus.WAIT_REVIEW, orderCaptor.getValue().getStatus());
        assertEquals(FIXED.toEpochMilli() + 24L * 3600 * 1000, orderCaptor.getValue().getReviewDeadlineMillis());

        assertEquals(1, draftCaptor.getValue().getVersion());
        assertEquals("CO20240101000001", draftCaptor.getValue().getCustomOrderId());

        assertEquals(CustomStatus.WAIT_REVIEW.name(), r.status());
        assertEquals(1, r.version());
    }

    @Test
    void attachmentRequired() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(waitingDraft()));

        BizException e = assertThrows(BizException.class,
                () -> useCase.submit(new SubmitDraftCmd("CO20240101000001", "", "empty.png", 0L)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }

    @Test
    void fileOver50MbRejected() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(waitingDraft()));

        BizException e = assertThrows(BizException.class,
                () -> useCase.submit(new SubmitDraftCmd("CO20240101000001", "F1", "huge.png", 51 * MB)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }

    @Test
    void resubmitAfterRejectBumpsVersionAndReturnsToWaitReview() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(rejected()));
        when(clock.instant()).thenReturn(FIXED);
        when(idGen.next("DR")).thenReturn("DR20240101000002");

        SubmitDraftResult r = useCase.submit(new SubmitDraftCmd("CO20240101000001", "F2", "logo_v2.png", 2 * MB));

        verify(customizeRepository).save(orderCaptor.capture());
        verify(customizeRepository).insertDraft(draftCaptor.capture());

        assertEquals(2, orderCaptor.getValue().getCurrentVersion());
        assertEquals(CustomStatus.WAIT_REVIEW, orderCaptor.getValue().getStatus());
        assertEquals(2, draftCaptor.getValue().getVersion());
        assertEquals(CustomStatus.WAIT_REVIEW.name(), r.status());
    }

    @Test
    void missingCustomOrderRejected() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.submit(new SubmitDraftCmd("CO20240101000001", "F1", "logo.png", 2 * MB)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }
}
