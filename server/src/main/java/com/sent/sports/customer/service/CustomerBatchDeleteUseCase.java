package com.sent.sports.customer.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.customer.mapper.CustomerMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 客户列表 / 授信企业客户：只打 customer.deleted 标记。 */
@Service
public class CustomerBatchDeleteUseCase {

    private final CustomerMapper customerMapper;

    public CustomerBatchDeleteUseCase(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.numeric(ids, customerMapper::batchMarkDeleted);
    }
}
