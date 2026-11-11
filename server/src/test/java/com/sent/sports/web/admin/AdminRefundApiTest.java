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
    @DisplayName("审核列表返回退款单号/订单号/金额/原因/申请时间")
    void listReturnsAuditPage() throws Exception {
        RefundDtos.RefundAuditVO vo = new RefundDtos.RefundAuditVO(
                "RF1", "O1", "PENDING", 12800L, "质量原因", 1_700_000_000_000L);
        when(refundQueryService.listAuditRefunds(any(PageQuery.class), any()))
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
                .andExpect(jsonPath("$.data.list[0].createdAt").value(1_700_000_000_000L));
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
