package com.sent.sports.customer.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.CustomerQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 客户查询实现（收口阶段）：列表从桩换成 {@code customer} 表真分页。
 * <p>此前恒返回空列表，导致「客户与会员」页以及一客一价的客户选择都拿不到数据。
 * 昵称为空时以「客户+ID」兜底，避免前端出现空白行；密码哈希等敏感字段不出该方法。</p>
 */
@Service
public class CustomerQueryServiceImpl implements CustomerQueryService {

    private final CustomerMapper customerMapper;

    public CustomerQueryServiceImpl(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @Override
    public PageResult<AdminCustomerDtos.CustomerRow> list(PageQuery pageQuery, String keyword,
                                                          String level, String type) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedLevel = (level == null || level.isBlank()) ? null : level.trim();
        String normalizedType = (type == null || type.isBlank()) ? null : type.trim().toUpperCase();
        Page<CustomerPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<CustomerPO> rows = customerMapper.selectAdminPage(
                page, normalizedKeyword, normalizedLevel, normalizedType);
        List<AdminCustomerDtos.CustomerRow> list = rows.getRecords().stream()
                .map(CustomerQueryServiceImpl::toRow)
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public Object detail(long customerId) {
        CustomerPO po = customerMapper.selectById(customerId);
        if (po == null || Integer.valueOf(1).equals(po.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toRow(po);
    }

    private static AdminCustomerDtos.CustomerRow toRow(CustomerPO po) {
        long id = po.getId() == null ? 0L : po.getId();
        String name = po.getNickname() == null || po.getNickname().isBlank()
                ? "客户" + id
                : po.getNickname().trim();
        return new AdminCustomerDtos.CustomerRow(id, po.getCustomerNo(), name, po.getPhone(),
                po.getType(), po.getLevelCode(), po.getOwnerStaffId(), po.getCreditLimitCent(),
                po.getGrowthValue() == null ? 0L : po.getGrowthValue(),
                po.getPointsBalance() == null ? 0L : po.getPointsBalance(),
                toMillis(po.getCreatedAt()),
                po.getCreditUsedCent(), po.getEnterpriseStatus());
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
