package com.sent.sports.web.admin;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.refund.controller.admin.AdminRefundController;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.service.RefundAuditUseCase;
import com.sent.sports.refund.service.RefundQueryService;
import com.sent.sports.refund.service.dto.AuditRefundCmd;
import com.sent.sports.refund.service.dto.AuditRefundResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端退款审核接口契约测试（收口阶段）：列表真实分页 + 通过/驳回动作映射到 APPROVE/REJECT。
 */
@WebMvcTest(AdminRefundController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminRefundApiTest extends BaseApiTest {

    @MockBean
    private RefundAuditUseCase refundAuditUseCase;

    @MockBean
    private RefundQueryService refundQueryService;

    @Test
    @DisplayName("审核列表：keyword 透传到 service（退款单号 / 订单号搜索）")
    void listPassesKeyword() throws Exception {
        when(refundQueryService.listAuditRefunds(any(PageQuery.class), isNull(), eq("RF2026")))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));

        mvc.perform(get("/api/admin/refunds")
                        .param("page", "1").param("size", "10").param("keyword", "RF2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(refundQueryService).listAuditRefunds(any(PageQuery.class), isNull(), eq("RF2026"));
    }

    @Test
    @DisplayName("审核列表返回退款单号/订单号/金额/原因/凭证图片/申请时间/人工复核标记")
    void listReturnsAuditPage() throws Exception {
        RefundDtos.RefundAuditVO vo = new RefundDtos.RefundAuditVO(
                "RF1", "O1", "PENDING", 12800L, "质量原因",
                List.of("2026/11/ab/a.png"), 1_700_000_000_000L, true);
        when(refundQueryService.listAuditRefunds(any(PageQuery.class), any(), isNull()))
                .thenReturn(PageResult.of(List.of(vo), 1L, 1, 10));

        mvc.perform(get("/api/admin/refunds").param("page", "1").param("size", "10").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].refundNo").value("RF1"))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("O1"))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.list[0].amountCent").value(12800))
                .andExpect(jsonPath("$.data.list[0].reason").value("质量原因"))
                .andExpect(jsonPath("$.data.list[0].images[0]").value("2026/11/ab/a.png"))
                .andExpect(jsonPath("$.data.list[0].needManualReview").value(true))
                .andExpect(jsonPath("$.data.list[0].createdAt").value(1_700_000_000_000L));
    }

    @Test
    @DisplayName("审核详情：GET /admin/refunds/{refundNo} 返回退款单 + 订单基本信息 + 商品明细")
    void detailReturnsRefundWithOrderBasics() throws Exception {
        RefundDtos.AuditOrderLineVO line = new RefundDtos.AuditOrderLineVO(
                9101L, "速干训练短袖", "黑色 / L", 2, 4990L, 9980L, 9980L, 2, "NONE");
        RefundDtos.AuditOrderVO order = new RefundDtos.AuditOrderVO(
                77L, "SO1", "PART_SHIPPED", "PAID", "PART_SHIPPED", "REFUNDING",
                9002L, "张三", "13800138000", "C9002", "PERSON", "LV1",
                1_767_200_400_000L, 1_767_200_700_000L, null,
                12800L, 0L, 0L, 0L, 12800L, 12800L,
                "张三", "13800138000", "广东省 深圳市 南山区 科技园路 5 号",
                true, "顺丰", "SF123456", 1_767_196_800_000L,
                "尽快发货", List.of(line));
        RefundDtos.RefundAuditDetailVO vo = new RefundDtos.RefundAuditDetailVO(
                "RF1", "SO1", "PENDING", 12800L, "128.00", 0L,
                "REFUND", "尺码不符", "CUSTOMER", 1, List.of("2026/11/ab/a.png"), null,
                1_767_200_000_000L, null, order, true);
        when(refundQueryService.getAuditDetail("RF1")).thenReturn(vo);

        mvc.perform(get("/api/admin/refunds/RF1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundNo").value("RF1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amountYuan").value("128.00"))
                .andExpect(jsonPath("$.data.images[0]").value("2026/11/ab/a.png"))
                .andExpect(jsonPath("$.data.order.orderNo").value("SO1"))
                .andExpect(jsonPath("$.data.order.customerName").value("张三"))
                .andExpect(jsonPath("$.data.order.customerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.order.address").value("广东省 深圳市 南山区 科技园路 5 号"))
                .andExpect(jsonPath("$.data.order.trackingNo").value("SF123456"))
                .andExpect(jsonPath("$.data.order.lines[0].skuName").value("速干训练短袖"))
                .andExpect(jsonPath("$.data.order.lines[0].qty").value(2))
                .andExpect(jsonPath("$.data.needManualReview").value(true));

        verify(refundQueryService).getAuditDetail("RF1");
    }

    @Test
    @DisplayName("审核通过映射 APPROVE 并带确认令牌头")
    void approveMapsToApprove() throws Exception {
        when(refundAuditUseCase.audit(any())).thenReturn(new AuditRefundResult("RF1", "REFUNDING"));

        mvc.perform(post("/api/admin/refunds/RF1/audit")
                        .header("X-Confirm-Token", "ct-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("approved", true, "remark", "审核通过"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REFUNDING"));

        ArgumentCaptor<AuditRefundCmd> captor = ArgumentCaptor.forClass(AuditRefundCmd.class);
        verify(refundAuditUseCase).audit(captor.capture());
        assertEquals("RF1", captor.getValue().refundNo());
        assertEquals("APPROVE", captor.getValue().action());
    }

    @Test
    @DisplayName("驳回映射 REJECT 并透传原因")
    void rejectMapsToReject() throws Exception {
        when(refundAuditUseCase.audit(any())).thenReturn(new AuditRefundResult("RF2", "CLOSED"));

        mvc.perform(post("/api/admin/refunds/RF2/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("approved", false, "remark", "凭证不足"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        ArgumentCaptor<AuditRefundCmd> captor = ArgumentCaptor.forClass(AuditRefundCmd.class);
        verify(refundAuditUseCase).audit(captor.capture());
        assertEquals("REJECT", captor.getValue().action());
        assertEquals("凭证不足", captor.getValue().reason());
    }
}
