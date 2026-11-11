package com.sent.sports.member.service.impl;

import com.sent.sports.member.domain.PointDeduction;
import com.sent.sports.member.service.PointFacade;
import org.springframework.stereotype.Service;

/**
 * 积分门面实现：委托领域 {@link PointDeduction} 计算抵扣上限。
 */
@Service
public class PointFacadeImpl implements PointFacade {
    @Override
    public long maxDeductibleCent(int availablePoints, long goodsTotalCent) {
        return PointDeduction.maxDeductibleCent(availablePoints, goodsTotalCent);
    }
}
