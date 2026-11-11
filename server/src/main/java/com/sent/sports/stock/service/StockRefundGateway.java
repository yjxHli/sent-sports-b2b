package com.sent.sports.stock.service;

/**
 * 库存退款门面（跨模块契约）：退款后释放该订单占用的库存预占额度（未发货部分回补待检）。
 * 供 refund 模块退款回调使用，符合 ArchUnit 规则 4（跨模块仅经 service 接口）。
 */
public interface StockRefundGateway {
    /** 释放指定订单退款对应的库存预占。 */
    void releaseOnRefund(String orderNo);
}
