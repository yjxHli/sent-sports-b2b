package com.sent.sports.finance.service;

/**
 * 订单结算门面（跨模块契约）：order 模块在「确认收货 → 订单完成」时经此接口
 * 生成应收并占用企业授信，不直接依赖 finance 的 mapper（ArchUnit 规则 4）。
 */
public interface OrderSettlementService {

    /**
     * 订单完成：一单一应收（{@code uk_receivable_order} 幂等），企业客户同步占用授信额度。
     * 个人客户只生成应收，不占用授信（无企业档案）。
     */
    void onOrderCompleted(String orderNo, long customerId, long payableCent);
}
