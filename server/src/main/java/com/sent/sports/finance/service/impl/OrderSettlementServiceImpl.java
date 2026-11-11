package com.sent.sports.finance.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.service.OrderSettlementService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 订单结算落账：订单完成即形成应收，企业客户同时占用授信额度。
 *
 * <p>幂等由 {@code uk_receivable_order} + {@code INSERT IGNORE} 承担：只有首次插入成功
 * 才会累加 {@code enterprise_profile.credit_used_cent} 并写入内存授信域对象，
 * 重复确认收货不会二次占用。核销时的释放由 {@code WriteOffUseCase} 对称处理。</p>
 */
@Service
public class OrderSettlementServiceImpl implements OrderSettlementService {

    private static final int PAYMENT_TERM_DAYS = 30;
    private static final long DAY_MILLIS = 24L * 3600 * 1000;

    private final ReceivableMapper receivableMapper;
    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final CreditLineRepository creditLineRepository;
    private final Clock clock;

    public OrderSettlementServiceImpl(ReceivableMapper receivableMapper,
                                      EnterpriseProfileMapper enterpriseProfileMapper,
                                      CreditLineRepository creditLineRepository,
                                      Clock clock) {
        this.receivableMapper = receivableMapper;
        this.enterpriseProfileMapper = enterpriseProfileMapper;
        this.creditLineRepository = creditLineRepository;
        this.clock = clock;
    }

    @Override
    public void onOrderCompleted(String orderNo, long customerId, long payableCent) {
        Long orderId = receivableMapper.selectOrderIdByOrderNo(orderNo);
        if (orderId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在，无法生成应收");
        }
        int inserted = receivableMapper.insertIgnore(orderId, customerId, payableCent,
                LocalDate.now().plusDays(PAYMENT_TERM_DAYS), "NORMAL");
        if (inserted == 0) {
            return; // 该订单已形成过应收，重放不重复占用授信
        }

        Long creditLimit = enterpriseProfileMapper.selectCreditLimit(customerId);
        if (creditLimit == null) {
            return; // 个人客户无企业授信档案
        }
        enterpriseProfileMapper.addCreditUsed(customerId, payableCent);
        long now = clock.instant().toEpochMilli();
        creditLineRepository.save(new CreditLine(customerId, creditLimit, payableCent,
                PAYMENT_TERM_DAYS, now, now + PAYMENT_TERM_DAYS * DAY_MILLIS,
                CreditLine.STATUS_ACTIVE));
    }
}
