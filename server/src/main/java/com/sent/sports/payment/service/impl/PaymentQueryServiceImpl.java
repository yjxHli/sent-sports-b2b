package com.sent.sports.payment.service.impl;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.payment.dto.PaymentDtos;
import com.sent.sports.payment.mapper.PaymentTxnMapper;
import com.sent.sports.payment.mapper.po.PaymentTxnPO;
import com.sent.sports.payment.service.PaymentQueryService;
import com.sent.sports.payment.service.dto.PaymentSummaryDto;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

/**
 * 支付查询实现。
 * <p>「我的流水 / 结果轮询」仍是 P4 占位；{@link #findSummary} 已接真实流水表——
 * 订单详情「订单信息」要显示支付方式与交易号，这只是一行只读查询，没有理由继续桩着。</p>
 */
@Service
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentTxnMapper paymentTxnMapper;

    public PaymentQueryServiceImpl(PaymentTxnMapper paymentTxnMapper) {
        this.paymentTxnMapper = paymentTxnMapper;
    }

    @Override
    public PageResult<PaymentDtos.PayResultVO> listMyPayments(PageQuery pageQuery) {
        // P5 接入实体与仓储后替换
        return PageResult.of(Collections.emptyList(), 0L, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public PaymentDtos.PayResultVO getPayResult(String orderNo) {
        // P5 接入实体与仓储后替换；当前默认 PENDING（前端轮询等待）
        return new PaymentDtos.PayResultVO(orderNo, "PENDING", null, null, null);
    }

    /**
     * 取最近一笔流水：一单可能因失败重试产生多条，展示口径与财务一致——以最后一次为准。
     * <p>查不到就返回空，不抛异常（「还没付款」是正常状态）。</p>
     */
    @Override
    public Optional<PaymentSummaryDto> findSummary(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return Optional.empty();
        }
        PaymentTxnPO txn = paymentTxnMapper.selectLatestByOrderNo(orderNo);
        if (txn == null) {
            return Optional.empty();
        }
        return Optional.of(new PaymentSummaryDto(
                txn.getOrderNo() == null ? orderNo : txn.getOrderNo(),
                txn.getChannel(), txn.getTxnNo(), txn.getChannelTradeNo(), txn.getStatus()));
    }
}
