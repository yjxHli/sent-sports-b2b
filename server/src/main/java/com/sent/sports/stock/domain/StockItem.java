package com.sent.sports.stock.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 库存实体（spec §6.2 / plan §7.2）：纯 Java 领域对象，无 Spring 依赖。
 * <pre>
 *   可售 = physical − locked − pending − frozen
 *   预占（lock）不足可售时抛 STOCK_NOT_ENOUGH(40910)
 *   各字段恒不为负（非法数量/超额操作均抛异常）
 * </pre>
 */
public class StockItem {

    private long physicalQty;
    private long lockedQty;
    private long pendingQty;
    private long frozenQty;

    public StockItem(long physicalQty, long lockedQty, long pendingQty, long frozenQty) {
        this.physicalQty = physicalQty;
        this.lockedQty = lockedQty;
        this.pendingQty = pendingQty;
        this.frozenQty = frozenQty;
    }

    /** 可售口径（对外唯一口径）。 */
    public long availableQty() {
        return Math.max(0, physicalQty - lockedQty - pendingQty - frozenQty);
    }

    /** 下单预占：可售不足抛 STOCK_NOT_ENOUGH(40910)。 */
    public void lock(long qty) {
        requirePositive(qty);
        if (availableQty() < qty) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        lockedQty += qty;
    }

    /** 释放预占（超时取消 / 未发货退款）：预占不足抛 STOCK_NOT_ENOUGH。 */
    public void release(long qty) {
        requirePositive(qty);
        if (lockedQty < qty) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        lockedQty -= qty;
    }

    /** 发货：扣实物与预占（防超发）；任一不足抛 STOCK_NOT_ENOUGH。 */
    public void ship(long qty) {
        requirePositive(qty);
        if (lockedQty < qty || physicalQty < qty) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        physicalQty -= qty;
        lockedQty -= qty;
    }

    /** 手工入库：实物增加。 */
    public void inbound(long qty) {
        requirePositive(qty);
        physicalQty += qty;
    }

    /** 报损出库：实物不足抛 STOCK_NOT_ENOUGH。 */
    public void loss(long qty) {
        requirePositive(qty);
        if (physicalQty < qty) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        physicalQty -= qty;
    }

    /** 冻结：可售不足抛 STOCK_NOT_ENOUGH。 */
    public void freeze(long qty) {
        requirePositive(qty);
        if (availableQty() < qty) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        frozenQty += qty;
    }

    /** 解冻。 */
    public void unfreeze(long qty) {
        requirePositive(qty);
        if (frozenQty < qty) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        frozenQty -= qty;
    }

    private static void requirePositive(long qty) {
        if (qty <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
    }

    public long getPhysicalQty() {
        return physicalQty;
    }

    public long getLockedQty() {
        return lockedQty;
    }

    public long getPendingQty() {
        return pendingQty;
    }

    public long getFrozenQty() {
        return frozenQty;
    }
}
