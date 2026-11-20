package com.sent.sports.customer;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import com.sent.sports.customer.service.impl.CustomerRegistrationServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户注册落库（2026-11-19 风控）：个人注册与企业注册共用这一条落库口，
 * 昵称命中敏感词必须在 insert 之前拒绝，不能先建号再改名。
 */
class CustomerRegistrationServiceImplTest extends BaseUnitTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private IdGen idGen;

    private CustomerRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("测试违禁词", "傻X", "加V"));
        service = new CustomerRegistrationServiceImpl(customerMapper, idGen, filter);
    }

    @Test
    @DisplayName("注册昵称命中敏感词：直接拒绝，不建账号")
    void sensitiveNicknameRejectedBeforeInsert() {
        BizException e = assertThrows(BizException.class,
                () -> service.register("13800001111", "secret123", "加V送福利"));

        assertEquals(ErrorCode.SENSITIVE_NICKNAME, e.getErrorCode());
        assertEquals("昵称/用户名包含违禁词，请修改后重试", e.resolvedMessage());
        verify(customerMapper, never()).insert(any(CustomerPO.class));
    }

    @Test
    @DisplayName("企业注册同样受控：昵称不合规也不建账号")
    void sensitiveNicknameRejectedForEnterpriseRegister() {
        BizException e = assertThrows(BizException.class,
                () -> service.registerEnterprise("13800002222", "secret123", "测试违禁词公司"));

        assertEquals(ErrorCode.SENSITIVE_NICKNAME, e.getErrorCode());
        verify(customerMapper, never()).insert(any(CustomerPO.class));
    }

    @Test
    @DisplayName("正常昵称照常建号：默认昵称「用户xxxx」不能被误杀")
    void normalNicknamePersists() {
        when(idGen.next("CUS")).thenReturn("CUS-1001");
        when(customerMapper.insert(any(CustomerPO.class))).thenAnswer(invocation -> {
            invocation.<CustomerPO>getArgument(0).setId(1001L);
            return 1;
        });

        RegisteredCustomer created = service.register("13800003333", "secret123", "用户3333");

        assertEquals(1001L, created.customerId());
        assertEquals("用户3333", created.nickname());
        verify(customerMapper).insert(any(CustomerPO.class));
    }
}
