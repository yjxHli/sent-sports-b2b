package com.sent.sports.customer.service;

/**
 * 客户积分台账门面（跨模块契约）：把「积分变动 + 流水」收敛成一次幂等写。
 *
 * <p>为什么必须落真库：{@code member} 模块的 {@code PointFacade} 只做规则计算，
 * 进程内的 {@code InMemoryPointFlowRepository} 也不会被写面调用——订单完成发积分、
 * 退款扣回积分如果只发领域事件，账面永远没有一条 {@code point_log}。</p>
 *
 * <p>幂等由 V47 的 {@code uk_point_source(customer_id, source_type, source_id)} 承担：
 * 同一来源重放时 {@code INSERT IGNORE} 返回 0 行，余额不再二次累加。</p>
 */
public interface CustomerPointService {

    /**
     * 记一笔积分变动并累加客户余额（增量更新，不按聚合值覆盖，避免并发丢更新）。
     *
     * @param customerId 客户主键
     * @param delta      积分增量（可为负：退款扣回、过期核销）
     * @param sourceType 来源类型（ORDER / REFUND / MANUAL），同时写入流水 type 与 source_type
     * @param sourceId   来源单号（订单号 / 退款单号），与 sourceType 共同构成幂等键
     * @return 落账后的积分余额；客户不存在或该来源已记过流水时返回当前余额（不存在返回 0）
     */
    long changePoints(long customerId, long delta, String sourceType, String sourceId);

    /**
     * 扣减积分（支付成功抵扣）：与 {@link #changePoints} 的区别是 <b>绝不允许扣成负数</b>。
     *
     * <p>并发正确性由数据库条件更新
     * {@code UPDATE customer SET points_balance = points_balance - #{points}
     * WHERE id = ? AND points_balance >= #{points}} 承担：影响行数为 0 即余额不足，
     * 抛 {@code POINT_NOT_ENOUGH} 让调用方（支付回调）整体回滚，绝不「先查后写」。</p>
     *
     * <p>幂等仍走 {@code uk_point_source}：同一 {@code sourceType + sourceId} 重放时
     * 不再扣第二次，直接返回当前余额。</p>
     *
     * @param customerId 客户主键
     * @param points     要扣的积分数（必须为正，1 积分 = 1 分）
     * @param sourceType 来源类型（抵扣固定 {@code ORDER_USE}，与发放的 {@code ORDER} 区分开：
     *                   同一笔订单既有抵扣流水又有发放流水，唯一键不能撞在一起）
     * @param sourceId   来源单号（订单号）
     * @return 扣减后的积分余额；重放时返回当前余额
     */
    long deductPoints(long customerId, long points, String sourceType, String sourceId);

    /**
     * 某来源已扣减的积分数（绝对值；没扣过返回 0）。
     *
     * <p>退款退积分以台账为准而不是订单头快照：未支付 / 已取消的订单也带着下单时算出的
     * {@code point_deduct_cent}，照它退等于凭空发积分。</p>
     *
     * @param sourceType 来源类型（抵扣为 {@code ORDER_USE}）
     * @param sourceId   来源单号（订单号）
     */
    long deductedPoints(long customerId, String sourceType, String sourceId);
}
