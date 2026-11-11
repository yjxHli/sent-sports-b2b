package com.sent.sports.stock.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P2-35 库存口径测试（TDD 红）：spec §6.2 / plan §7.2。
 * 可售 = physical − locked − pending − frozen；不足不可锁（STOCK_NOT_ENOUGH 40910）；
 * 各字段不为负。
 */
class StockItemTest {

    @Test
    void availableIsPhysicalMinusOthers() {
        StockItem s = new StockItem(100, 10, 5, 5);
        assertEquals(80L, s.availableQty());
    }

    @Test
    void lockReducesAvailable() {
        StockItem s = new StockItem(100, 10, 5, 5);
        s.lock(30);
        assertEquals(40L, s.getLockedQty());
        assertEquals(50L, s.availableQty());
    }

    @Test
    void lockInsufficientThrows() {
        StockItem s = new StockItem(100, 10, 5, 5); // 可售 80
        BizException ex = assertThrows(BizException.class, () -> s.lock(90));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH, ex.getErrorCode());
    }

    @Test
    void nonPositiveQtyRejected() {
        StockItem s = new StockItem(100, 0, 0, 0);
        BizException ex = assertThrows(BizException.class, () -> s.lock(0));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void shipMovesLockedToPhysical() {
        StockItem s = new StockItem(100, 40, 0, 0);
        s.ship(10);
        assertEquals(90L, s.getPhysicalQty());
        assertEquals(30L, s.getLockedQty());
    }

    @Test
    void shipOverLockedThrows() {
        StockItem s = new StockItem(100, 10, 0, 0);
        BizException ex = assertThrows(BizException.class, () -> s.ship(20));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH, ex.getErrorCode());
    }

    @Test
    void inboundIncreasesPhysical() {
        StockItem s = new StockItem(100, 0, 0, 0);
        s.inbound(50);
        assertEquals(150L, s.getPhysicalQty());
    }

    @Test
    void lossOverPhysicalThrows() {
        StockItem s = new StockItem(100, 0, 0, 0);
        BizException ex = assertThrows(BizException.class, () -> s.loss(120));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH, ex.getErrorCode());
    }

    @Test
    void freezeReducesAvailable() {
        StockItem s = new StockItem(100, 0, 0, 0);
        s.freeze(20);
        assertEquals(20L, s.getFrozenQty());
        assertEquals(80L, s.availableQty());
    }

    @Test
    void unfreezeRestores() {
        StockItem s = new StockItem(100, 0, 0, 20);
        s.unfreeze(20);
        assertEquals(0L, s.getFrozenQty());
        assertEquals(100L, s.availableQty());
    }

    @Test
    void releaseReducesLocked() {
        StockItem s = new StockItem(100, 40, 0, 0);
        s.release(40);
        assertEquals(0L, s.getLockedQty());
        assertEquals(100L, s.getPhysicalQty()); // 预占释放不影响实物
    }

    @Test
    void releaseOverLockedThrows() {
        StockItem s = new StockItem(100, 10, 0, 0);
        BizException ex = assertThrows(BizException.class, () -> s.release(20));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH, ex.getErrorCode());
    }
}
