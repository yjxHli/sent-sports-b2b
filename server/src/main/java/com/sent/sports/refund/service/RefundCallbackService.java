package com.sent.sports.refund.service;

import com.sent.sports.refund.domain.RefundOrder;

/**
 * 退款回调服务（跨模块/内部契约）：审核通过后经此执行实际退款回调，
 * 负责更新支付流水为 REFUNDED、退积分优先、回补待检、释放额度、全退关单。实现见 P3-22/23。
 */
public interface RefundCallbackService {
    /** 执行退款回调（基于已审核的退款单）。 */
    void process(RefundOrder refundOrder);
}
