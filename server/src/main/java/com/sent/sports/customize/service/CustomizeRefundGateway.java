package com.sent.sports.customize.service;

/**
 * 定制退款判定门面（跨模块契约）：refund 模块经此判定某订单行定制是否已排产/完工（进入生产后不可退款），
 * 不直接依赖 customize 领域类型（ArchUnit 规则 4）。无定制记录的订单行一律视为可退。
 */
public interface CustomizeRefundGateway {
    /** 该订单行定制是否已排产/完工（进入生产后不可退款）。 */
    boolean isRefundBlocked(String orderNo, long skuId);
}
