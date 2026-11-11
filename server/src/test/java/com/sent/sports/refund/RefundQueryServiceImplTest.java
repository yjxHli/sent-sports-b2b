package com.sent.sports.refund;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundDetailPO;
import com.sent.sports.refund.mapper.po.RefundListPO;
import com.sent.sports.refund.service.impl.RefundQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 退款查询实现测试（收口阶段，TDD 绿）：用户端列表真查、详情归属隔离（非本人 → NOT_FOUND）。
 */
@ExtendWith(MockitoExtension.class)
class RefundQueryServiceImplTest {

    @Mock
    private RefundOrderMapper refundOrderMapper;

    private RefundQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefundQueryServiceImpl(refundOrderMapper);
    }

    @Test
    @DisplayName("我的售后：真查分页并按 customerId + status 过滤，金额分→元映射正确")
    void listMyRefundsQueriesRealRows() {
        when(refundOrderMapper.selectMyPage(any(), eq(9002L), eq("REFUNDING"))).thenAnswer(inv -> {
            Page<RefundListPO> p = inv.getArgument(0);
            RefundListPO po = new RefundListPO();
            po.setRefundNo("RF1");
            po.setOrderNo("SO1");
            po.setStatus("REFUNDING");
            po.setAmountCent(5000L);
            p.setRecords(List.of(po));
            return p;
        });
        when(refundOrderMapper.countMy(9002L, "REFUNDING")).thenReturn(1L);

        PageQuery q = new PageQuery();
        q.setPage(1);
        q.setSize(10);
        var res = service.listMyRefunds(q, 9002L, "REFUNDING");

        assertEquals(1L, res.getTotal());
        assertEquals("RF1", res.getList().get(0).refundNo());
        assertEquals("50.00", res.getList().get(0).amountYuan());
    }

    @Test
    @DisplayName("售后详情：本人归属命中返回详情（含类型/原因/申诉次数/时间线）")
    void getDetailReturnsOwnedRefund() {
        RefundDetailPO po = new RefundDetailPO();
        po.setRefundNo("RF1");
        po.setOrderNo("SO1");
        po.setStatus("REFUNDED");
        po.setAmountCent(5000L);
        po.setType("REFUND");
        po.setReason("质量问题");
        po.setApplicantType("CUSTOMER");
        po.setAppealCount(0L);
        po.setCustomerId(9002L);
        po.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        po.setRefundedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        when(refundOrderMapper.selectDetailByRefundNo("RF1")).thenReturn(po);

        RefundDtos.RefundDetailVO v = service.getDetail("RF1", 9002L);

        assertEquals("REFUNDED", v.status());
        assertEquals("50.00", v.amountYuan());
        assertEquals("质量问题", v.reason());
        assertEquals(0, v.appealCount());
    }

    @Test
    @DisplayName("售后详情：非本人归属返回 NOT_FOUND（归属隔离）")
    void getDetailRejectsOtherCustomer() {
        RefundDetailPO po = new RefundDetailPO();
        po.setCustomerId(9999L);
        when(refundOrderMapper.selectDetailByRefundNo("RF2")).thenReturn(po);

        BizException ex = assertThrows(BizException.class, () -> service.getDetail("RF2", 9002L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("售后详情：退款单不存在返回 NOT_FOUND")
    void getDetailNotFound() {
        when(refundOrderMapper.selectDetailByRefundNo("NOPE")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getDetail("NOPE", 9002L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
