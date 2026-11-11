package com.sent.sports.finance.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import com.sent.sports.finance.service.CreditFacade;
import org.springframework.stereotype.Service;

/**
 * 授信门面实现：只读预检 + 关单时按应收记录回冲已用额度。
 * 金额一律以「分」从库里取，不接受调用方传入，避免超放额度。
 */
@Service
public class CreditFacadeImpl implements CreditFacade {

    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final ReceivableMapper receivableMapper;

    public CreditFacadeImpl(EnterpriseProfileMapper enterpriseProfileMapper,
                            ReceivableMapper receivableMapper) {
        this.enterpriseProfileMapper = enterpriseProfileMapper;
        this.receivableMapper = receivableMapper;
    }

    @Override
    public void assertAvailable(long customerId, long payableCent) {
        // 个人客户无企业授信档案：selectCreditLimit 返回 null，不参与授信约束，直接放行。
        Long limit = enterpriseProfileMapper.selectCreditLimit(customerId);
        if (limit == null) {
            return;
        }
        long used = enterpriseProfileMapper.selectCreditUsed(customerId);
        if (used + payableCent > limit) {
            throw new BizException(ErrorCode.CREDIT_NOT_ENOUGH, "信用额度不足");
        }
    }

    @Override
    public void releaseCreditForOrder(String orderNo) {
        Long orderId = receivableMapper.selectOrderIdByOrderNo(orderNo);
        if (orderId == null) {
            return;
        }
        ReceivablePO receivable = receivableMapper.selectByOrderId(orderId);
        if (receivable == null || receivable.getCustomerId() == null) {
            return;
        }
        long amount = receivable.getAmountCent() == null ? 0L : receivable.getAmountCent();
        if (amount <= 0L) {
            return;
        }
        // SQL 内 GREATEST(credit_used_cent - amount, 0) 兜底，不会把额度冲成负数。
        enterpriseProfileMapper.subtractCreditUsed(receivable.getCustomerId(), amount);
    }
}
