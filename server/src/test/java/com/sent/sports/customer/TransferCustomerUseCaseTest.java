package com.sent.sports.customer;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customer.domain.Customer;
import com.sent.sports.customer.domain.TransferLog;
import com.sent.sports.customer.repository.CustomerRepository;
import com.sent.sports.customer.repository.TransferLogRepository;
import com.sent.sports.customer.service.TransferCustomerUseCase;
import com.sent.sports.customer.service.dto.TransferCustomerCmd;
import com.sent.sports.customer.service.dto.TransferCustomerResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户改派用例测试（P3-48，TDD 红）：spec / 验收——
 * 原因必填（PARAM_INVALID）、不改历史订单归属（仅更新当前归属并写流转日志）、写 TransferLog。
 * 此时 Customer/TransferCustomerUseCase 等尚未实现，编译即失败（红）。
 */
class TransferCustomerUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private TransferLogRepository transferLogRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private TransferCustomerUseCase useCase;
    @Captor
    private ArgumentCaptor<TransferLog> logCaptor;

    @Test
    void transferUpdatesOwnerAndWritesLog() {
        Customer customer = new Customer(1L, 5L, NOW.toEpochMilli());
        when(customerRepository.findByCustomerId(1L)).thenReturn(Optional.of(customer));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("TL")).thenReturn("TL0001");

        TransferCustomerResult r = useCase.transfer(new TransferCustomerCmd(1L, 7L, "区域调整", 9L));

        verify(customerRepository).save(any(Customer.class));
        verify(transferLogRepository).save(logCaptor.capture());
        TransferLog log = logCaptor.getValue();
        assertEquals(1L, log.customerId());
        assertEquals(5L, log.fromStaffId());
        assertEquals(7L, log.toStaffId());
        assertEquals("区域调整", log.reason());
        assertEquals(7L, r.toStaffId());
    }

    @Test
    void reasonMustNotBeBlank() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.transfer(new TransferCustomerCmd(1L, 7L, "  ", 9L)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(customerRepository, never()).save(any());
        verify(transferLogRepository, never()).save(any());
    }

    @Test
    void customerNotFound() {
        when(customerRepository.findByCustomerId(2L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.transfer(new TransferCustomerCmd(2L, 7L, "调整", 9L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
