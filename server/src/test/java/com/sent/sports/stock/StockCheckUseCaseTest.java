package com.sent.sports.stock;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.stock.domain.StockCheck;
import com.sent.sports.stock.domain.StockFlow;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.repository.StockCheckRepository;
import com.sent.sports.stock.repository.StockFlowRepository;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.StockCheckUseCase;
import com.sent.sports.stock.service.dto.ApproveCheckCmd;
import com.sent.sports.stock.service.dto.ApproveCheckResult;
import com.sent.sports.stock.service.dto.CreateCheckCmd;
import com.sent.sports.stock.service.dto.CreateCheckResult;
import com.sent.sports.stock.service.dto.RecordCheckCmd;
import com.sent.sports.stock.service.dto.RecordCheckResult;
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
 * 盘点用例测试（P3-34，TDD 红）：覆盖 spec §8.3 / 验收——
 * 实盘差异计算（record 生成 diff）、审批后调账（approve 按 diff 增减实物并写 CHECK 流水）、令牌由 P4 校验。
 * 此时 StockCheck/StockCheckUseCase 等尚未实现，编译即失败（红）。
 */
class StockCheckUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockCheckRepository stockCheckRepository;
    @Mock
    private StockFlowRepository stockFlowRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private StockCheckUseCase useCase;
    @Captor
    private ArgumentCaptor<StockCheck> checkCaptor;
    @Captor
    private ArgumentCaptor<StockFlow> flowCaptor;
    @Captor
    private ArgumentCaptor<StockItem> itemCaptor;

    @Test
    void createCapturesBookQtyFromPhysical() {
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(100, 0, 0, 0)));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("SC")).thenReturn("SC0001");

        CreateCheckResult r = useCase.create(new CreateCheckCmd(1L, 7L));

        verify(stockCheckRepository).save(checkCaptor.capture());
        StockCheck c = checkCaptor.getValue();
        assertEquals(1L, c.getSkuId());
        assertEquals(100L, c.getBookQty());
        assertEquals("DRAFT", c.getStatus());
        assertEquals("SC0001", r.checkNo());
        assertEquals(100L, r.bookQty());
        assertEquals("DRAFT", r.status());
    }

    @Test
    void recordComputesDiffAndSetsCounted() {
        StockCheck draft = new StockCheck("SC0001", 1L, 100L, 7L, NOW.toEpochMilli());
        when(stockCheckRepository.findByCheckId("SC0001")).thenReturn(Optional.of(draft));
        when(clock.instant()).thenReturn(NOW);

        RecordCheckResult r = useCase.record(new RecordCheckCmd("SC0001", 80L, 7L));

        verify(stockCheckRepository).save(checkCaptor.capture());
        StockCheck saved = checkCaptor.getValue();
        assertEquals("COUNTED", saved.getStatus());
        assertEquals(80L, saved.getActualQty());
        assertEquals(-20L, saved.getDiffQty());
        assertEquals(-20L, r.diffQty());
        assertEquals("COUNTED", r.status());
    }

    @Test
    void approveNegativeDiffLossesStockAndWritesCheckFlow() {
        StockCheck counted = new StockCheck("SC0001", 1L, 100L, 7L, NOW.toEpochMilli());
        counted.record(80L, NOW.toEpochMilli()); // diff = -20, COUNTED
        when(stockCheckRepository.findByCheckId("SC0001")).thenReturn(Optional.of(counted));
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(100, 0, 0, 0)));
        when(clock.instant()).thenReturn(NOW);

        ApproveCheckResult r = useCase.approve(new ApproveCheckCmd("SC0001", 7L));

        verify(stockRepository).save(eq(1L), itemCaptor.capture());
        assertEquals(80L, itemCaptor.getValue().getPhysicalQty());
        verify(stockFlowRepository).save(flowCaptor.capture());
        StockFlow f = flowCaptor.getValue();
        assertEquals("CHECK", f.bizType());
        assertEquals(-20L, f.changeQty());
        assertEquals(100L, f.beforeQty());
        assertEquals(80L, f.afterQty());
        assertEquals("APPROVED", r.status());
        assertEquals(80L, r.afterPhysicalQty());
    }

    @Test
    void approvePositiveDiffInboundsStock() {
        StockCheck counted = new StockCheck("SC0001", 1L, 100L, 7L, NOW.toEpochMilli());
        counted.record(120L, NOW.toEpochMilli()); // diff = +20, COUNTED
        when(stockCheckRepository.findByCheckId("SC0001")).thenReturn(Optional.of(counted));
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(100, 0, 0, 0)));
        when(clock.instant()).thenReturn(NOW);

        ApproveCheckResult r = useCase.approve(new ApproveCheckCmd("SC0001", 7L));

        verify(stockRepository).save(eq(1L), itemCaptor.capture());
        assertEquals(120L, itemCaptor.getValue().getPhysicalQty());
        verify(stockFlowRepository).save(flowCaptor.capture());
        assertEquals(20L, flowCaptor.getValue().changeQty());
        assertEquals(120L, r.afterPhysicalQty());
    }

    @Test
    void cannotRecordUnlessDraft() {
        StockCheck counted = new StockCheck("SC0001", 1L, 100L, 7L, NOW.toEpochMilli());
        counted.record(80L, NOW.toEpochMilli()); // already COUNTED
        when(stockCheckRepository.findByCheckId("SC0001")).thenReturn(Optional.of(counted));

        BizException e = assertThrows(BizException.class,
                () -> useCase.record(new RecordCheckCmd("SC0001", 70L, 7L)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(stockCheckRepository, never()).save(any());
    }

    @Test
    void cannotApproveUnlessCounted() {
        StockCheck draft = new StockCheck("SC0001", 1L, 100L, 7L, NOW.toEpochMilli());
        when(stockCheckRepository.findByCheckId("SC0001")).thenReturn(Optional.of(draft));

        BizException e = assertThrows(BizException.class,
                () -> useCase.approve(new ApproveCheckCmd("SC0001", 7L)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(stockRepository, never()).save(anyLong(), any());
        verify(stockFlowRepository, never()).save(any());
    }
}
