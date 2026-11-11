package com.sent.sports.customer;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.impl.CustomerQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户列表从空桩换成真分页后：昵称兜底、字段映射与筛选下推必须可验证。
 */
@ExtendWith(MockitoExtension.class)
class CustomerQueryServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerQueryServiceImpl service;

    private static CustomerPO po(long id, String nickname, String phone, String level) {
        CustomerPO po = new CustomerPO();
        po.setId(id);
        po.setCustomerNo("C" + id);
        po.setNickname(nickname);
        po.setPhone(phone);
        po.setType("PERSON");
        po.setLevelCode(level);
        po.setDeleted(0);
        return po;
    }

    private static Page<CustomerPO> pageOf(CustomerPO... rows) {
        Page<CustomerPO> page = new Page<>(1, 20);
        page.setRecords(List.of(rows));
        page.setTotal(rows.length);
        return page;
    }

    @Test
    @DisplayName("客户列表返回真实行：昵称/手机号回显，关键字/等级/类型下推到 SQL")
    void listReturnsRowsAndPushesFilters() {
        when(customerMapper.selectAdminPage(
                ArgumentMatchers.<Page<CustomerPO>>any(), any(), any(), any()))
                .thenReturn(pageOf(po(9002L, "验收客户", "13800138000", "GOLD")));

        PageResult<AdminCustomerDtos.CustomerRow> result =
                service.list(new PageQuery(), " 1380 ", "GOLD", "enterprise");

        assertEquals(1, result.getTotal());
        AdminCustomerDtos.CustomerRow row = result.getList().get(0);
        assertEquals(9002L, row.customerId());
        assertEquals("验收客户", row.name());
        assertEquals("13800138000", row.phone());
        assertEquals("GOLD", row.level());
        verify(customerMapper).selectAdminPage(
                ArgumentMatchers.<Page<CustomerPO>>any(), ArgumentMatchers.eq("1380"),
                ArgumentMatchers.eq("GOLD"), ArgumentMatchers.eq("ENTERPRISE"));
    }

    @Test
    @DisplayName("空白客户类型归一为 null，不追加 type 条件")
    void listNormalizesBlankType() {
        when(customerMapper.selectAdminPage(
                ArgumentMatchers.<Page<CustomerPO>>any(), isNull(), isNull(), isNull()))
                .thenReturn(pageOf(po(91001L, "企业客户", "13900009101", null)));

        service.list(new PageQuery(), "  ", null, null);

        verify(customerMapper).selectAdminPage(
                ArgumentMatchers.<Page<CustomerPO>>any(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("授信额度随企业档案联查回显：否则授信成功后列表仍显示「未授信」")
    void listCarriesCreditFromEnterpriseProfile() {
        CustomerPO row = po(91154L, "演示企业151有限公司", "13900009154", "LV2");
        row.setType("ENTERPRISE");
        row.setCreditLimitCent(11_000_000L);
        row.setCreditUsedCent(1_000_000L);
        row.setEnterpriseStatus("ACTIVE");
        when(customerMapper.selectAdminPage(
                ArgumentMatchers.<Page<CustomerPO>>any(), isNull(), isNull(),
                ArgumentMatchers.eq("ENTERPRISE")))
                .thenReturn(pageOf(row));

        AdminCustomerDtos.CustomerRow vo =
                service.list(new PageQuery(), null, null, "ENTERPRISE").getList().get(0);

        assertEquals(11_000_000L, vo.creditLimitCent());
        assertEquals(1_000_000L, vo.creditUsedCent());
        assertEquals("ACTIVE", vo.enterpriseStatus());
    }

    @Test
    @DisplayName("昵称为空的客户用「客户+ID」兜底，避免前端出现空白行")
    void listFallsBackNameWhenNicknameBlank() {
        when(customerMapper.selectAdminPage(
                ArgumentMatchers.<Page<CustomerPO>>any(), isNull(), isNull(), isNull()))
                .thenReturn(pageOf(po(91001L, "  ", "13900009101", null)));

        AdminCustomerDtos.CustomerRow row = service.list(new PageQuery(), null, null, null).getList().get(0);

        assertEquals("客户91001", row.name());
    }

    @Test
    @DisplayName("客户详情：不存在或已软删时抛 NOT_FOUND")
    void detailRejectsMissingOrDeleted() {
        when(customerMapper.selectById(9999L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.detail(9999L));

        CustomerPO deleted = po(9003L, "已删客户", "13800138001", null);
        deleted.setDeleted(1);
        when(customerMapper.selectById(9003L)).thenReturn(deleted);
        assertThrows(BizException.class, () -> service.detail(9003L));
    }
}
