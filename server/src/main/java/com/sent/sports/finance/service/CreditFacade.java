package com.sent.sports.finance.service;

/**
 * 授信门面（跨模块契约）：供 order 模块在下单时做信用额度软校验，
 * 不直接暴露 finance 领域类型，满足 ArchUnit 规则 4（跨模块只走 service）。
 * <p>语义为「乐观预检」：下单时按当前已占用额度 + 本次应付判断是否会超限额。
 * 真正的额度占用发生在结算环节（OrderSettlementServiceImpl 调 addCreditUsed，原子 UPDATE），
 * 此处只做下单前的快速拦截，避免明显超额的订单进入后续流程。</p>
 */
public interface CreditFacade {

    /**
     * 校验客户可用授信额度是否足以覆盖本次应付（分）。
     *
     * @param customerId  客户 ID
     * @param payableCent 本次应付金额（分），必须是后端计算值，绝非客户端传入
     * @throws com.sent.sports.common.error.BizException 额度不足时抛 CREDIT_NOT_ENOUGH
     */
    void assertAvailable(long customerId, long payableCent);

    /**
     * 关单时释放该订单占用的企业信用额度。
     * <p>额度只在「确认收货 → 生成应收台账」时由 {@code addCreditUsed} 占用，
     * 因此仅当该订单确实存在应收记录才回冲，避免误减其它订单的占用额；
     * 无应收（关单都发生在收货前）时为空操作。</p>
     */
    void releaseCreditForOrder(String orderNo);
}
