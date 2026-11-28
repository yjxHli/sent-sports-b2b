package com.sent.sports.payment.service.impl;

import com.sent.sports.payment.mapper.PaymentTxnMapper;
import com.sent.sports.payment.mapper.po.PaymentTxnPO;
import com.sent.sports.payment.service.PaymentQueryService;
import com.sent.sports.payment.service.dto.PaymentSummaryDto;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 支付查询实现。
 *
 * <p>2026-11-28 P2 批次：两个桩方法（我的流水 / 结果轮询）已删除，本类只保留
 * {@link #findSummary}——订单详情「订单信息」要显示支付方式与交易号，这是一行真实只读查询。</p>
 */
@Service
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentTxnMapper paymentTxnMapper;

    public PaymentQueryServiceImpl(PaymentTxnMapper paymentTxnMapper) {
        this.paymentTxnMapper = paymentTxnMapper;
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
