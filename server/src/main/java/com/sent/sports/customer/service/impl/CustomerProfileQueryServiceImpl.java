package com.sent.sports.customer.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.dto.user.MeVO;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.CustomerProfileQueryService;
import org.springframework.stereotype.Service;

/**
 * 用户端个人资料查询实现（收口阶段）：经 {@link CustomerMapper} 读取 V3 的 {@code customer} 表。
 * <p>软删除以 {@code deleted} 列判定；等级 / 积分 / 成长值直接取自该表对应列。</p>
 */
@Service
public class CustomerProfileQueryServiceImpl implements CustomerProfileQueryService {

    private final CustomerMapper customerMapper;

    public CustomerProfileQueryServiceImpl(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @Override
    public MeVO profile(long customerId) {
        CustomerPO po = customerMapper.selectById(customerId);
        if (po == null || (po.getDeleted() != null && po.getDeleted() > 0)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new MeVO(
                po.getId(),
                po.getCustomerNo(),
                po.getNickname(),
                po.getPhone(),
                po.getEmail(),
                po.getType(),
                po.getLevelCode(),
                po.getGrowthValue() == null ? 0L : po.getGrowthValue(),
                po.getPointsBalance() == null ? 0L : po.getPointsBalance(),
                po.getStatus());
    }
}
