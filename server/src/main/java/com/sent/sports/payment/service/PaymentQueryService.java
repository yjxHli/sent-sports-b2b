package com.sent.sports.payment.service;

import com.sent.sports.payment.service.dto.PaymentSummaryDto;

import java.util.Optional;

/**
 * 支付查询门面（P4-19 接口契约，跨模块可用）。
 *
 * <p>2026-11-28 P2 批次：删除 {@code listMyPayments} / {@code getPayResult} 两个桩方法——
 * 它们恒返回空列表与 PENDING，接口在、数据假，比没有更危险（前端会以为「轮询中」）。
 * 「我的流水」与「支付结果轮询」前端均无调用方，等真正需要时再接真库重开。</p>
 */
public interface PaymentQueryService {

    /**
     * 某订单最近一笔支付摘要（订单详情「订单信息」用）。
     * <p>未发起支付时返回 {@link Optional#empty()}，调用方按「未支付」空态渲染，
     * 不要用异常表达「没付过钱」这种正常情况。</p>
     */
    Optional<PaymentSummaryDto> findSummary(String orderNo);
}
