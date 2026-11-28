package com.sent.sports.stock;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.mapper.StockFlowMapper;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import com.sent.sports.stock.mapper.po.StockFlowPO;
import com.sent.sports.stock.repository.StockRepositoryImpl;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * 库存仓储实现测试（2026-11-28 批次 1，P0-05）。
 *
 * <p>背景：{@code save()} 原先「普通快照读 + 四列绝对值覆盖写」，入库/报损/盘点期间
 * 并发的 {@code lockStock} 会被覆盖回去，可用量虚高 → 超卖。修复口径两条：</p>
 * <ol>
 *   <li>读必须走 {@code selectBySkuIdForUpdate}（行锁），把「读-改-写」串行化；</li>
 *   <li>写必须带 {@code version} 条件，影响 0 行说明仍被并发改动 → 40901，绝不静默覆盖。</li>
 * </ol>
 */
class StockRepositoryImplTest extends BaseUseCaseTest {

    private static final long SKU_ID = 9101L;

    @Mock
    private StockMapper stockMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;

    @InjectMocks
    private StockRepositoryImpl repository;

    private SkuStockPO row(int physical, int locked, long version) {
        SkuStockPO po = new SkuStockPO();
        po.setSkuId(SKU_ID);
        po.setPhysicalQty(physical);
        po.setLockedQty(locked);
        po.setPendingQty(0);
        po.setFrozenQty(0);
        po.setSafetyQty(10);
        po.setVersion(version);
        return po;
    }

    @Test
    @DisplayName("save 必须加行锁读（selectBySkuIdForUpdate），不得再用普通快照读")
    void saveReadsWithRowLock() {
        when(stockMapper.selectBySkuIdForUpdate(SKU_ID)).thenReturn(row(100, 5, 7L));
        when(stockMapper.updateLedger(SKU_ID, 110L, 5L, 0L, 0L, 7L)).thenReturn(1);

        repository.save(SKU_ID, new StockItem(110L, 5L, 0L, 0L));

        verify(stockMapper).selectBySkuIdForUpdate(SKU_ID);
        verify(stockMapper, never()).selectBySkuId(SKU_ID);
    }

    @Test
    @DisplayName("save 把读到的 version 作为条件传给 updateLedger（乐观锁兜底）")
    void savePassesVersionToConditionalUpdate() {
        when(stockMapper.selectBySkuIdForUpdate(SKU_ID)).thenReturn(row(100, 5, 42L));
        when(stockMapper.updateLedger(SKU_ID, 100L, 5L, 0L, 0L, 42L)).thenReturn(1);

        repository.save(SKU_ID, new StockItem(100L, 5L, 0L, 0L));

        verify(stockMapper).updateLedger(SKU_ID, 100L, 5L, 0L, 0L, 42L);
    }

    @Test
    @DisplayName("并发改动导致 0 行更新：抛 STATE_CONFLICT(40901)，不补流水、不静默覆盖")
    void saveRejectsConcurrentModification() {
        when(stockMapper.selectBySkuIdForUpdate(SKU_ID)).thenReturn(row(100, 5, 7L));
        when(stockMapper.updateLedger(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> repository.save(SKU_ID, new StockItem(110L, 5L, 0L, 0L)));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(stockFlowMapper, never()).insert(any(StockFlowPO.class));
    }

    @Test
    @DisplayName("台账不存在：抛 NOT_FOUND，不做任何写入")
    void saveRejectsMissingLedger() {
        when(stockMapper.selectBySkuIdForUpdate(SKU_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> repository.save(SKU_ID, new StockItem(10L, 0L, 0L, 0L)));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(stockMapper, never()).updateLedger(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("物理库存变化补一条 ADJUST 流水：before/after/changeQty 与台账对齐")
    void saveRecordsAdjustFlowWithDelta() {
        when(stockMapper.selectBySkuIdForUpdate(SKU_ID)).thenReturn(row(100, 5, 7L));
        when(stockMapper.updateLedger(SKU_ID, 130L, 5L, 0L, 0L, 7L)).thenReturn(1);

        repository.save(SKU_ID, new StockItem(130L, 5L, 0L, 0L));

        ArgumentCaptor<StockFlowPO> captor = ArgumentCaptor.forClass(StockFlowPO.class);
        verify(stockFlowMapper).insert(captor.capture());
        StockFlowPO flow = captor.getValue();
        assertEquals("ADJUST", flow.getBizType());
        assertEquals(30, flow.getChangeQty().intValue());
        assertEquals(100, flow.getBeforeQty().intValue());
        assertEquals(130, flow.getAfterQty().intValue());
    }
}
