package com.sent.sports.customize.service;

/**
 * 定制退款判定门面（跨模块契约）：refund 模块经此判定某订单行定制能否退款，
 * 不直接依赖 customize 领域类型（ArchUnit 规则 4）。
 *
 * <p>契约给的是三档处置（{@link CustomRefundPolicy}）而不是「是否拦截」布尔：
 * APPROVED（审核通过未排产）属于「可申请但需人工复核」，布尔无法表达。
 * 调用方按 {@code policyOf} 分流；{@link #isRefundBlocked} 只是「只关心拦不拦」时的便捷视图，
 * 实现恒等于 {@code policyOf(...).isBlocked()}，因此不会与档位判定漂移。</p>
 */
public interface CustomizeRefundGateway {

    /**
     * 判定该订单行定制的退款处置档位。
     *
     * @param orderNo 订单号
     * @param skuId   订单行 SKU
     * @return 处置档位；无定制记录时返回 {@link CustomRefundPolicy#NOT_CUSTOM}，永不返回 null
     */
    CustomRefundPolicy policyOf(String orderNo, long skuId);

    /** 该订单行定制是否绝对不可退款（{@code policyOf(...) == BLOCKED} 的便捷视图）。 */
    default boolean isRefundBlocked(String orderNo, long skuId) {
        return policyOf(orderNo, skuId).isBlocked();
    }
}
