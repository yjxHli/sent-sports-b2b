package com.sent.sports.stock;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.stock.domain.StockFlow;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.event.StockInboundEvent;
import com.sent.sports.stock.repository.StockFlowRepository;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.InboundUseCase;
import com.sent.sports.stock.service.dto.InboundCmd;
import com.sent.sports.stock.service.dto.InboundResult;
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
 * 手工入库用例测试（P3-30，TDD 红）：覆盖 spec §8.3 / 验收 A19——
 * 数量>0、写库存流水、可售库存增加、触发到货提醒事件。
 * 此时 InboundUseCase 等尚未实现，编译即失败（红）。
 */
class InboundUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockFlowRepository stockFlowRepository;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private InboundUseCase useCase;
    @Captor
    private ArgumentCaptor<StockFlow> flowCaptor;
    @Captor
    private ArgumentCaptor<StockInboundEvent> eventCaptor;

    @Test
    void inboundIncreasesAvailableWritesFlowAndPublishesEvent() {
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(0, 0, 0, 0)));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("SF")).thenReturn("SF0001");
        when(idGen.next("IB")).thenReturn("IB0001");

        InboundResult r = useCase.inbound(new InboundCmd(1L, 100L, "INIT", 7L, "首备货"));

        verify(stockRepository).save(eq(1L), any(StockItem.class));
        verify(stockFlowRepository).save(flowCaptor.capture());
        StockFlow f = flowCaptor.getValue();
        assertEquals(1L, f.skuId());
        assertEquals("INBOUND", f.bizType());
        assertEquals(100L, f.changeQty());
        assertEquals(0L, f.beforeQty());
        assertEquals(100L, f.afterQty());
        verify(eventPublisher).publish(eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().skuId());
        assertEquals(100L, eventCaptor.getValue().changeQty());
        assertEquals(100L, eventCaptor.getValue().afterPhysicalQty());
        assertEquals(100L, r.afterAvailableQty());
    }

    @Test
    void quantityMustBePositive() {
        when(stockRepository.findBySku(1L)).thenReturn(Optional.of(new StockItem(0, 0, 0, 0)));

        BizException e = assertThrows(BizException.class,
                () -> useCase.inbound(new InboundCmd(1L, 0L, "INIT", 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(stockRepository, never()).save(anyLong(), any());
        verify(stockFlowRepository, never()).save(any());
    }
}
