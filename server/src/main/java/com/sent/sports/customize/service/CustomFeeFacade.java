package com.sent.sports.customize.service;

/**
 * 定制服务费门面（跨模块契约）：cart 试算与 order 下单共用的唯一计费入口。
 *
 * <p><b>P0 修复（审计 R6）</b>：此前 {@code CartQuoteUseCase} 写死 {@code 1000L * qty}、
 * {@code CreateOrderUseCase} 写死常量 {@code CUSTOM_FEE_PER_UNIT_CENT}，而真正带
 * 面数单价与数量阶梯折扣的 {@code CustomFeeCalculator} 只有测试在调用——运营调价要改两个模块，
 * 且两边一旦不同步就会出现「购物车试算价比实付便宜」。</p>
 *
 * <p>本门面把计费引擎接回主链路：单价来自配置（{@code app.custom-fee.*}），
 * 面数与阶梯规则由引擎计算，cart / order 只拿同一个数。</p>
 */
public interface CustomFeeFacade {

    /**
     * 该行定制服务费（分）= 单件服务费 × 数量。
     *
     * @param qty 定制件数（订单行数量）
     * @return 服务费总额（分）；qty ≤ 0 或未开启定制时返回 0
     */
    long feeCent(int qty);
}
