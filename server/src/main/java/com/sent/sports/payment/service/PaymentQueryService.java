package com.sent.sports.payment.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.payment.dto.PaymentDtos;
import com.sent.sports.payment.service.dto.PaymentSummaryDto;

import java.util.Optional;

/**
 * 支付查询门面（P4-19 接口契约，跨模块可用）。
 * <p>payment 读面（我的流水 / 支付结果轮询）尚未建设（属 P5 DB 范畴），当前由
 * {@code PaymentQueryServiceImpl} 桩实现占位；Phase 5 接入实体与仓储后替换。契约（接口签名）保持稳定。</p>
 */
public interface PaymentQueryService {

    /** 我的支付流水：按当前用户 + 分页。 */
    PageResult<PaymentDtos.PayResultVO> listMyPayments(PageQuery pageQuery);

    /** 支付结果轮询：返回 PAID / FAILED / PENDING 三态。 */
    PaymentDtos.PayResultVO getPayResult(String orderNo);

    /**
     * 某订单最近一笔支付摘要（订单详情「订单信息」用）。
     * <p>未发起支付时返回 {@link Optional#empty()}，调用方按「未支付」空态渲染，
     * 不要用异常表达「没付过钱」这种正常情况。</p>
     */
    Optional<PaymentSummaryDto> findSummary(String orderNo);
}
