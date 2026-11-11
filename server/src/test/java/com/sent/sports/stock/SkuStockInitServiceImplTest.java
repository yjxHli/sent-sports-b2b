package com.sent.sports.stock;

import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.impl.SkuStockInitServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 商品编辑保存 SKU 时，初始库存必须经 stock 模块服务端口同步并写流水。 */
@ExtendWith(MockitoExtension.class)
class SkuStockInitServiceImplTest {

    @Mock
    private StockMapper stockMapper;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private SkuStockInitServiceImpl service;

    @Test
    @DisplayName("syncPhysicalQty 先确保台账存在，再按编辑页库存写物理库存")
    void syncPhysicalQtyInitializesLedgerAndSetsPhysicalQty() {
        when(stockRepository.findBySku(9207L)).thenReturn(Optional.of(new StockItem(0L, 0L, 0L, 0L)));

        service.syncPhysicalQty(9207L, 50);

        verify(stockMapper).insertIfAbsent(9207L, 0);
        ArgumentCaptor<StockItem> captor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockRepository).save(eq(9207L), captor.capture());
        assertEquals(50L, captor.getValue().getPhysicalQty());
        assertEquals(0L, captor.getValue().getLockedQty());
    }

    @Test
    @DisplayName("syncPhysicalQty 不会把物理库存压到已锁定/待出/冻结量之下")
    void syncPhysicalQtyDoesNotDropBelowReservedQty() {
        when(stockRepository.findBySku(9207L))
                .thenReturn(Optional.of(new StockItem(100L, 20L, 5L, 5L)));

        service.syncPhysicalQty(9207L, 10);

        ArgumentCaptor<StockItem> captor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockRepository).save(eq(9207L), captor.capture());
        assertEquals(30L, captor.getValue().getPhysicalQty());
        assertEquals(20L, captor.getValue().getLockedQty());
        assertEquals(5L, captor.getValue().getPendingQty());
        assertEquals(5L, captor.getValue().getFrozenQty());
    }
}
