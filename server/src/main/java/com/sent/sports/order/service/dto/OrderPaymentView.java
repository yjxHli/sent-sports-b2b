package com.sent.sports.order.service.dto;

/**
 * 订单支付态中立视图（供 payment 等跨模块消费，不暴露 order 领域类型，符合 ArchUnit 规则 4）。
 *
 * <p>{@code customerId} 与 {@code pointDeductCent} 是支付成功时结算积分的必需信息：
 * 回调要把「本单抵了多少积分」从客户账上扣掉，缺了这两个字段 payment 模块只能再查一次订单
 * （多一次跨模块往返，还可能读到两次之间被改过的快照）。</p>
 */
public record OrderPaymentView(String orderNo, String status, String payStatus, long payableCent,
                               long customerId, long pointDeductCent) {

    /** 兼容只关心支付态的调用方（用例断言 / 报表）：不涉及积分归属。 */
    public OrderPaymentView(String orderNo, String status, String payStatus, long payableCent) {
        this(orderNo, status, payStatus, payableCent, 0L, 0L);
    }
}
