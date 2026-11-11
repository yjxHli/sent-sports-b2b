package com.sent.sports.customer.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customer.domain.Customer;
import com.sent.sports.customer.domain.TransferLog;
import com.sent.sports.customer.repository.CustomerRepository;
import com.sent.sports.customer.repository.TransferLogRepository;
import com.sent.sports.customer.service.dto.TransferCustomerCmd;
import com.sent.sports.customer.service.dto.TransferCustomerResult;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 客户改派用例（P3-49）：spec / 验收——
 * 原因必填（PARAM_INVALID）、不改历史订单归属（仅更新当前 owner 并写流转日志）、写 TransferLog。
 * 仅依赖 customer 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class TransferCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final TransferLogRepository transferLogRepository;
    private final IdGen idGen;
    private final Clock clock;

    public TransferCustomerUseCase(CustomerRepository customerRepository, TransferLogRepository transferLogRepository,
                                  IdGen idGen, Clock clock) {
        this.customerRepository = customerRepository;
        this.transferLogRepository = transferLogRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public TransferCustomerResult transfer(TransferCustomerCmd cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Customer customer = customerRepository.findByCustomerId(cmd.customerId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        long from = customer.getOwnerStaffId() == null ? 0L : customer.getOwnerStaffId();
        customer.transferTo(cmd.toStaffId());
        long now = clock.instant().toEpochMilli();
        customerRepository.save(customer);
        transferLogRepository.save(new TransferLog(idGen.next("TL"), cmd.customerId(), from,
                cmd.toStaffId(), cmd.reason(), cmd.operatorId(), now));
        return new TransferCustomerResult(cmd.customerId(), from, cmd.toStaffId());
    }
}
