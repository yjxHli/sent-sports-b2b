package com.sent.sports.stock;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.stock.domain.StockFlow;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.repository.StockFlowRepository;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.LossUseCase;
import com.sent.sports.stock.service.dto.LossCmd;
import com.sent.sports.stock.service.dto.LossResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报损用例测试（P3-32，TDD 红）：覆盖 spec §8.3 / 验收——
 * 不得超实物（STOCK_NOT_ENOUGH）、必须填原因（PARAM_INVALID）、写库存流水（LOSS）、可售同步减少。
 * 此时 LossUseCase 等尚未实现，编译即失败（红）。
 */
class LossUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockFlowRepository stockFlowRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private LossUseCase useCase;
    @Captor
    private ArgumentCaptor<StockFlow> flowCaptor;

    @Test
    void lossDecreasesPhysicalWritesFlowAndUpdatesAvailable() {
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(100, 0, 0, 0)));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("SF")).thenReturn("SF0001");
        when(idGen.next("LS")).thenReturn("LS0001");

        LossResult r = useCase.loss(new LossCmd(1L, 30L, "破损报损", 7L, "仓库受潮"));

        verify(stockRepository).save(eq(1L), any(StockItem.class));
        verify(stockFlowRepository).save(flowCaptor.capture());
        StockFlow f = flowCaptor.getValue();
        assertEquals(1L, f.skuId());
        assertEquals("LOSS", f.bizType());
        assertEquals(30L, f.changeQty());
        assertEquals(100L, f.beforeQty());
        assertEquals(70L, f.afterQty());
        assertEquals(70L, r.afterPhysicalQty());
        assertEquals(70L, r.afterAvailableQty());
    }

    @Test
    void reasonMustNotBeBlank() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.loss(new LossCmd(1L, 10L, "   ", 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(stockRepository, never()).save(anyLong(), any());
        verify(stockFlowRepository, never()).save(any());
    }

    @Test
    void mustNotExceedPhysical() {
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(50, 0, 0, 0)));

        BizException e = assertThrows(BizException.class,
                () -> useCase.loss(new LossCmd(1L, 80L, "报损", 7L, "x")));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH, e.getErrorCode());
        verify(stockRepository, never()).save(anyLong(), any());
        verify(stockFlowRepository, never()).save(any());
    }

    @Test
    void quantityMustBePositive() {
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(100, 0, 0, 0)));

        BizException e = assertThrows(BizException.class,
                () -> useCase.loss(new LossCmd(1L, 0L, "报损", 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(stockRepository, never()).save(anyLong(), any());
        verify(stockFlowRepository, never()).save(any());
    }
}
