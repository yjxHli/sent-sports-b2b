package com.sent.sports.order.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务专用的订单只读查询口（跨模块契约）：只返回订单号，不把 order 的 PO / domain 漏给 job 包。
 * 业务动作仍由 ConfirmReceiptUseCase / CancelOrderUseCase 承担。
 */
public interface OrderJobQueryService {

    /** 已送达/发货后超过 before 仍待收货的订单号（限量）。 */
    List<String> findAwaitingReceiveOrderNos(LocalDateTime before, int limit);

    /** 截止 now 仍未支付且未取消的订单号（限量）。 */
    List<String> findTimeoutUnpaidOrderNos(LocalDateTime now, int limit);
}
