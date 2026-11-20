package com.sent.sports.member.service;

/**
 * 积分门面（跨模块契约）：封装积分抵扣计算与退款扣回，供 cart/order/refund 调用，
 * 不直接暴露 member 领域类型，满足 ArchUnit 规则 4。
 */
public interface PointFacade {
    /** 本次最多可抵扣金额（分）= min(可用积分折分, 商品总额 × 30%)。 */
    long maxDeductibleCent(int availablePoints, long goodsTotalCent);

    /**
     * 退款成功：扣回该退款金额对应的积分（1 元 = 1 积分，与发放同口径）。
     *
     * <p>同步调用、真实落 {@code point_log}：退款回调把「扣回积分」当成完成步骤之一，
     * 走事件总线时事件一丢业务流程照常提交，账面看起来成功但权益没有回退（R4）。</p>
     *
     * <p>余额不足时允许记为负积分（spec §6.4：后续消费优先抵扣）。同一退款单重放不会二次扣回。</p>
     *
     * @param customerId 客户主键
     * @param refundNo   退款单号（幂等键的来源单号）
     * @param refundCent 退款金额（分）
     * @return 实际扣回的积分数（正数；金额不足 1 元时为 0）
     */
    long revokeForRefund(long customerId, String refundNo, long refundCent);

    /**
     * 支付成功：扣减本单实际抵扣的积分（<b>1 积分 = 1 分</b>，与 {@link #maxDeductibleCent} 同口径）。
     *
     * <p>同步调用、真实落 {@code point_log}（type = {@code USE}，{@code source_id} = 订单号）：
     * 只落 {@code orders.point_deduct_cent} 而不扣余额，等于用户可以拿同一批积分无限次抵扣。</p>
     *
     * <p>余额不足（并发把积分花在别的订单上）时抛 {@code POINT_NOT_ENOUGH}，调用方必须让支付回调失败——
     * 钱收了、积分没扣的口子比「让用户重试支付」危险得多。同一订单重放不会二次扣减。</p>
     *
     * @param customerId       客户主键
     * @param orderNo          订单号（幂等键来源）
     * @param pointDeductCent  本单实际抵扣金额（分）；0 表示没用积分，不产生流水
     * @return 扣减后的积分余额；未使用积分时返回 0
     */
    long deductPoints(long customerId, String orderNo, long pointDeductCent);

    /**
     * 该订单<b>实际扣减过</b>的抵扣积分（没扣过返回 0）。
     *
     * <p>退款退积分要按台账而不是 {@code orders.point_deduct_cent}：后者是下单快照，
     * 未支付/已取消的订单也带着非 0 值，照它退等于凭空发积分。</p>
     */
    long deductedPointsForOrder(long customerId, String orderNo);

    /**
     * 退款成功：把本单应退的抵扣积分退回客户（1 积分 = 1 分），落
     * {@code point_log}（type = {@code REFUND_RETURN}，{@code source_id} = 退款单号）。
     *
     * <p>幂等靠 {@code uk_point_source(customer_id, source_type, source_id)}：同一退款单重放不会二次退回。
     * 与退款侧的 {@code refundPoints}（按 1 元 = 1 积分扣回赠送积分）用不同的来源类型，互不吞。</p>
     *
     * @param points 应退积分数；<= 0 时不写流水
     */
    void returnForRefund(long customerId, String refundNo, long points);
}
