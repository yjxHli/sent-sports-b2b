package com.sent.sports.customize;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.ProduceCustomUseCase;
import com.sent.sports.customize.service.dto.ProduceCustomCmd;
import com.sent.sports.customize.service.dto.ProduceCustomResult;
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
 * 排产完工用例测试（P3-28，TDD 红）：覆盖 plan §7.5 / 验收 A7——
 * 审核通过(APPROVED)方可排产→生产中(IN_PRODUCTION)、生产中方可完工→已完工(FINISHED)并记完工时间；
 * 非对应状态排产/完工均拒绝；"已排产不可退款、完工才可发货"由退款/发货门面保障（A7 见网关实现）。
 * 此时 ProduceCustomUseCase 等尚未实现，编译即失败（红）。
 */
class ProduceUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private CustomizeRepository customizeRepository;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private ProduceCustomUseCase useCase;
    @Captor
    private ArgumentCaptor<CustomOrder> orderCaptor;

    private CustomOrder approved() {
        return new CustomOrder("CO20240101000001", CustomStatus.APPROVED, 1, 0, 0L);
    }

    private CustomOrder inProduction() {
        return new CustomOrder("CO20240101000001", CustomStatus.IN_PRODUCTION, 1, 0, 0L);
    }

    @Test
    void startProductionMovesToInProductionAndRecordsStartTime() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(approved()));
        when(clock.instant()).thenReturn(NOW);

        ProduceCustomResult r = useCase.produce(new ProduceCustomCmd("CO20240101000001", "START_PRODUCTION"));

        verify(customizeRepository).save(orderCaptor.capture());
        assertEquals(CustomStatus.IN_PRODUCTION, orderCaptor.getValue().getStatus());
        assertEquals(NOW.toEpochMilli(), orderCaptor.getValue().getStartedAtMillis());
        assertEquals(CustomStatus.IN_PRODUCTION.name(), r.status());
    }

    @Test
    void finishMovesToFinishedAndRecordsFinishTime() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(inProduction()));
        when(clock.instant()).thenReturn(NOW);

        ProduceCustomResult r = useCase.produce(new ProduceCustomCmd("CO20240101000001", "FINISH"));

        verify(customizeRepository).save(orderCaptor.capture());
        assertEquals(CustomStatus.FINISHED, orderCaptor.getValue().getStatus());
        assertEquals(NOW.toEpochMilli(), orderCaptor.getValue().getFinishedAtMillis());
        assertEquals(CustomStatus.FINISHED.name(), r.status());
    }

    @Test
    void cannotStartProductionWhenNotApproved() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(inProduction()));

        BizException e = assertThrows(BizException.class,
                () -> useCase.produce(new ProduceCustomCmd("CO20240101000001", "START_PRODUCTION")));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
    }

    @Test
    void cannotFinishWhenNotInProduction() {
        when(customizeRepository.findByCustomOrderId("CO20240101000001")).thenReturn(Optional.of(approved()));

        BizException e = assertThrows(BizException.class,
                () -> useCase.produce(new ProduceCustomCmd("CO20240101000001", "FINISH")));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
    }
}
