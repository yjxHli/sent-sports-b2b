package com.sent.sports.refund;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundAuditDetailPO;
import com.sent.sports.refund.mapper.po.RefundAuditItemPO;
import com.sent.sports.refund.mapper.po.RefundDetailPO;
import com.sent.sports.refund.mapper.po.RefundListPO;
import com.sent.sports.refund.mapper.po.RefundOrderLinePO;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    @DisplayName("审核列表：带出用户上传的凭证图片（images_json 解码成 fileId 列表）")
    void auditListCarriesProofImages() {
        when(refundOrderMapper.selectAuditPage(any(), isNull(), isNull())).thenAnswer(inv -> {
            Page<RefundAuditItemPO> p = inv.getArgument(0);
            RefundAuditItemPO po = new RefundAuditItemPO();
            po.setRefundNo("RF1");
            po.setOrderNo("SO1");
            po.setStatus("PENDING");
            po.setAmountCent(5000L);
            po.setReason("尺码不符");
            po.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            po.setImagesJson("[\"2026/11/ab/a.png\",\"2026/11/ab/b.png\"]");
            p.setRecords(List.of(po));
            return p;
        });
        when(refundOrderMapper.countAudit(isNull(), isNull())).thenReturn(1L);

        PageQuery q = new PageQuery();
        q.setPage(1);
        q.setSize(10);
        var res = service.listAuditRefunds(q, null, null);

        assertEquals(List.of("2026/11/ab/a.png", "2026/11/ab/b.png"), res.getList().get(0).images());
    }

    @Test
    @DisplayName("审核列表：没有图片时返回空列表而不是 null（前端可直接 v-for）")
    void auditListWithoutImagesReturnsEmptyList() {
        when(refundOrderMapper.selectAuditPage(any(), isNull(), isNull())).thenAnswer(inv -> {
            Page<RefundAuditItemPO> p = inv.getArgument(0);
            RefundAuditItemPO po = new RefundAuditItemPO();
            po.setRefundNo("RF2");
            p.setRecords(List.of(po));
            return p;
        });
        when(refundOrderMapper.countAudit(isNull(), isNull())).thenReturn(1L);

        PageQuery q = new PageQuery();
        q.setPage(1);
        q.setSize(10);
        var res = service.listAuditRefunds(q, null, null);

        assertEquals(List.of(), res.getList().get(0).images());
    }

    @Test
    @DisplayName("售后详情：带出用户申请时上传的凭证图片")
    void detailCarriesProofImages() {
        RefundDetailPO po = new RefundDetailPO();
        po.setRefundNo("RF1");
        po.setCustomerId(9002L);
        po.setImagesJson("[\"2026/11/ab/a.png\"]");
        when(refundOrderMapper.selectDetailByRefundNo("RF1")).thenReturn(po);

        RefundDtos.RefundDetailVO v = service.getDetail("RF1", 9002L);

        assertEquals(List.of("2026/11/ab/a.png"), v.images());
    }

    @Test
    @DisplayName("审核详情：一次带出退款单 + 订单基本信息 + 客户 + 商品明细（审核员不必再开第二个页面）")
    void auditDetailCarriesOrderBasics() {
        RefundAuditDetailPO po = auditDetailPo();
        when(refundOrderMapper.selectAuditDetail("RF1")).thenReturn(po);
        when(refundOrderMapper.selectOrderLines(77L)).thenReturn(List.of(linePo()));

        RefundDtos.RefundAuditDetailVO v = service.getAuditDetail("RF1");

        assertEquals("RF1", v.refundNo());
        assertEquals("SO1", v.orderNo());
        assertEquals("PENDING", v.status());
        assertEquals(Long.valueOf(12800L), v.amountCent());
        assertEquals("128.00", v.amountYuan());
        assertEquals("尺码不符", v.reason());
        assertEquals(List.of("2026/11/ab/a.png"), v.images());
        assertEquals(1, v.appealCount());

        RefundDtos.AuditOrderVO order = v.order();
        assertEquals("SO1", order.orderNo());
        assertEquals("PART_SHIPPED", order.status());
        assertEquals("PART_SHIPPED", order.shipStatus(), "发货状态按订单主状态推导，不额外多一个语义");
        assertEquals("张三", order.customerName());
        assertEquals("13800138000", order.customerPhone());
        assertEquals("广东省 深圳市 南山区 科技园路 5 号", order.address());
        assertEquals("顺丰", order.carrier());
        assertEquals("SF123456", order.trackingNo());
        assertEquals(1, order.lines().size());
        assertEquals("速干训练短袖", order.lines().get(0).skuName());
        assertEquals(Long.valueOf(9980L), order.lines().get(0).linePaidCent(),
                "行实付 = 行金额 − 分摊优惠，退款审核按它对账");
    }

    @Test
    @DisplayName("审核详情：退款单不存在返回 NOT_FOUND（不返回半截空对象）")
    void auditDetailNotFound() {
        when(refundOrderMapper.selectAuditDetail("NOPE")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getAuditDetail("NOPE"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("审核详情：订单行查询为空时返回空列表而不是 null（前端可直接 v-for）")
    void auditDetailWithoutLinesReturnsEmptyList() {
        when(refundOrderMapper.selectAuditDetail("RF1")).thenReturn(auditDetailPo());
        when(refundOrderMapper.selectOrderLines(77L)).thenReturn(List.of());

        RefundDtos.RefundAuditDetailVO v = service.getAuditDetail("RF1");

        assertEquals(List.of(), v.order().lines());
    }

    @Test
    @DisplayName("审核详情：地址快照与物流信息缺失时降级为空值，不抛异常")
    void auditDetailToleratesMissingSnapshot() {
        RefundAuditDetailPO po = auditDetailPo();
        po.setAddressJson(null);
        po.setCarrier(null);
        po.setTrackingNo(null);
        po.setShippedAt(null);
        when(refundOrderMapper.selectAuditDetail("RF1")).thenReturn(po);
        when(refundOrderMapper.selectOrderLines(77L)).thenReturn(List.of());

        RefundDtos.RefundAuditDetailVO v = service.getAuditDetail("RF1");

        assertEquals("", v.order().address());
        assertNull(v.order().carrier());
        assertNull(v.order().trackingNo());
        assertNull(v.order().shippedAt());
    }

    /** 审核详情投影：退款单 + 订单头 + 客户 + 物流（模拟一次联查的结果）。 */
    private static RefundAuditDetailPO auditDetailPo() {
        RefundAuditDetailPO po = new RefundAuditDetailPO();
        po.setRefundNo("RF1");
        po.setOrderNo("SO1");
        po.setStatus("PENDING");
        po.setAmountCent(12800L);
        po.setType("REFUND");
        po.setReason("尺码不符");
        po.setApplicantType("CUSTOMER");
        po.setAppealCount(1L);
        po.setImagesJson("[\"2026/11/ab/a.png\"]");
        po.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        po.setOrderId(77L);
        po.setCustomerId(9002L);
        po.setOrderStatus("PART_SHIPPED");
        po.setPayStatus("PAID");
        po.setAfterSaleStatus("REFUNDING");
        po.setGoodsAmountCent(12800L);
        po.setFreightCent(0L);
        po.setPointDeductCent(0L);
        po.setDiscountCent(0L);
        po.setPayableCent(12800L);
        po.setPaidCent(12800L);
        po.setOrderCreatedAt(LocalDateTime.of(2025, 12, 31, 9, 0));
        po.setPaidAt(LocalDateTime.of(2025, 12, 31, 9, 5));
        po.setCustomerName("张三");
        po.setCustomerPhone("13800138000");
        po.setCustomerNo("C9002");
        po.setCustomerType("PERSON");
        po.setCustomerLevel("LV1");
        po.setAddressJson("{\"receiver\":\"张三\",\"phone\":\"13800138000\",\"province\":\"广东省\","
                + "\"city\":\"深圳市\",\"district\":\"南山区\",\"detail\":\"科技园路 5 号\"}");
        po.setCarrier("顺丰");
        po.setTrackingNo("SF123456");
        po.setShippedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        return po;
    }

    // ------------------------------------------------------------------
    // 售后角标计数（2026-11-16，TDD 红）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("售后计数：全部单量 + 在途（PENDING/REFUNDING）两条口径，个人中心角标不再恒为 0")
    void countMyRefunds() {
        when(refundOrderMapper.countMy(9002L, null)).thenReturn(3L);
        when(refundOrderMapper.countMyInFlight(9002L)).thenReturn(2L);

        RefundDtos.RefundCountVO vo = service.countMyRefunds(9002L);

        assertEquals(3L, vo.total());
        assertEquals(2L, vo.inFlight());
    }

    @Test
    @DisplayName("售后计数：未登录（customerId=0）返回 0，且不查库")
    void countMyRefundsAnonymous() {
        RefundDtos.RefundCountVO vo = service.countMyRefunds(0L);

        assertEquals(0L, vo.total());
        assertEquals(0L, vo.inFlight());
        org.mockito.Mockito.verify(refundOrderMapper, org.mockito.Mockito.never())
                .countMy(anyLong(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(refundOrderMapper, org.mockito.Mockito.never())
                .countMyInFlight(anyLong());
    }

    private static RefundOrderLinePO linePo() {
        RefundOrderLinePO line = new RefundOrderLinePO();
        line.setSkuId(9101L);
        line.setSkuName("速干训练短袖");
        line.setSpec("黑色 / L");
        line.setQty(2);
        line.setUnitPriceCent(4990L);
        line.setAmountCent(9980L);
        line.setDiscountAllocCent(0L);
        line.setLinePaidCent(9980L);
        line.setShippedQty(2);
        line.setCustomStatus("NONE");
        return line;
    }
}
