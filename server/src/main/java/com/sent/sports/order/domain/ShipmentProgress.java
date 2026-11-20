package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 发货进度重算（plan §7.4）：集中计算订单主状态，禁止各处自行判断。
 * <pre>
 * 已发 = 0          → 待发货
 * 0 < 已发 < 总量   → 部分发货
 * 已发 ≥ 总量       → 待收货
 * </pre>
 */
public final class ShipmentProgress {

    public static OrderStatus recalc(int totalQty, int shippedQty) {
        if (totalQty <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单总量必须为正");
        }
        if (shippedQty <= 0) {
            return OrderStatus.WAIT_SHIP;
        }
        if (shippedQty < totalQty) {
            return OrderStatus.PART_SHIPPED;
        }
        return OrderStatus.WAIT_RECEIVE;
    }

    /**
     * 由当前主状态与本次发货进度选出迁移动作；实际准入仍由 {@link OrderStateMachine} 裁决。
     * 非法当前状态（如 WAIT_RECEIVE / COMPLETED）不会被这里放行：算出的动作会被状态机拒绝。
     */
    public static OrderAction actionFor(OrderStatus current, int totalQty, int shippedQty) {
        OrderStatus target = recalc(totalQty, shippedQty);
        if (target == OrderStatus.WAIT_RECEIVE) {
            return OrderAction.SHIP_FULL;
        }
        return current == OrderStatus.WAIT_SHIP ? OrderAction.SHIP_PARTIAL : OrderAction.SHIP_CONTINUE;
    }
}
