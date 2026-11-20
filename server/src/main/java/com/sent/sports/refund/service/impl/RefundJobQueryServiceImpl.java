package com.sent.sports.refund.service.impl;

import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.service.RefundJobQueryService;
import org.springframework.stereotype.Service;

@Service
public class RefundJobQueryServiceImpl implements RefundJobQueryService {

    private final RefundOrderMapper refundOrderMapper;

    public RefundJobQueryServiceImpl(RefundOrderMapper refundOrderMapper) {
        this.refundOrderMapper = refundOrderMapper;
    }

    @Override
    public int countPending(int limit) {
        return refundOrderMapper.selectPending(limit).size();
    }
}
