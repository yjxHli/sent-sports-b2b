package com.sent.sports.refund.service;

import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.refund.domain.RefundOrder;
import org.springframework.stereotype.Service;

/**
 * 退款成功站内信（2026-11-27）：所有 REFUNDED 路径共用——审核回调成功与管理端关单/定制超时自动退款。
 * <p>订单视图取不到客户时静默跳过：退款本身已经落地，不能因为一条通知失败而回滚或阻断退款。</p>
 */
@Service
public class RefundSuccessNotifier {

    private final OrderRefundViewService orderRefundViewService;
    private final MessageSendService messageSendService;

    public RefundSuccessNotifier(OrderRefundViewService orderRefundViewService,
                                 MessageSendService messageSendService) {
        this.orderRefundViewService = orderRefundViewService;
        this.messageSendService = messageSendService;
    }

    /** 对一笔已成功的退款发一封站内信，重复调用由调用方的状态机/幂等键挡住。 */
    public void notify(RefundOrder order) {
        orderRefundViewService.getView(order.getOrderNo())
                .map(view -> view.customerId())
                .filter(id -> id > 0L)
                .ifPresent(id -> messageSendService.send(new InboxMessage(
                        id,
                        "ORDER",
                        "退款成功",
                        "退款单 " + order.getRefundNo() + "（订单 " + order.getOrderNo()
                                + "）已退款 ¥" + MoneyUtil.toYuan(order.getAmountCent())
                                + "，款项将原路退回。",
                        "REFUND",
                        order.getRefundNo())));
    }
}
