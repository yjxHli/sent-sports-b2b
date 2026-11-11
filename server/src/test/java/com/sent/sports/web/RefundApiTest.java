package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.refund.controller.RefundController;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.service.RefundApplyUseCase;
import com.sent.sports.refund.service.RefundAuditUseCase;
import com.sent.sports.refund.service.RefundQueryService;
import com.sent.sports.refund.service.dto.AuditRefundResult;
import com.sent.sports.refund.service.dto.RefundApplyResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 退款接口契约测试（P4-20，红→绿）。
 * 超退 REFUND_OVERFLOW 40940、状态跟踪 200、申诉一次 200、我的售后空页。
 */
@WebMvcTest(RefundController.class)
@AutoConfigureMockMvc(addFilters = false)
class RefundApiTest extends BaseApiTest {

    @MockBean
    private RefundApplyUseCase refundApplyUseCase;

    @MockBean
    private RefundAuditUseCase refundAuditUseCase;

    @MockBean
    private RefundQueryService refundQueryService;

    @Test
    @DisplayName("申请退款成功返回退款单与状态 200（状态跟踪）")
    void applySuccessReturns200() throws Exception {
        when(refundApplyUseCase.apply(any()))
                .thenReturn(new RefundApplyResult("RF1", "O1", "PENDING", 1000L));
        mvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody("O1", 1000L, "质量原因", "proof-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundNo").value("RF1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amountCent").value(1000))
                .andExpect(jsonPath("$.data.amountYuan").value("10.00"));
    }

    @Test
    @DisplayName("超退返回 REFUND_OVERFLOW 40940")
    void overRefundReturns40940() throws Exception {
        when(refundApplyUseCase.apply(any()))
                .thenThrow(new BizException(ErrorCode.REFUND_OVERFLOW));
        mvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody("O1", 9999999L, "多退", "proof-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40940));
    }

    @Test
    @DisplayName("申诉一次返回 200（回到待审核）")
    void appealOnceReturns200() throws Exception {
        when(refundAuditUseCase.audit(any()))
                .thenReturn(new AuditRefundResult("RF1", "PENDING"));
        mvc.perform(post("/api/refunds/RF1/appeal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundNo").value("RF1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("我的售后单返回空分页 200")
    void listMyRefundsReturnsEmptyPage() throws Exception {
        when(refundQueryService.listMyRefunds(any(), anyLong(), any()))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/refunds").param("page", "1").param("size", "10")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("我的售后单返回真实行（金额分→元映射正确）")
    void listMyRefundsReturnsRows() throws Exception {
        RefundDtos.RefundVO row = new RefundDtos.RefundVO("RF1", "SO1", "REFUNDING", 5000L, "50.00");
        when(refundQueryService.listMyRefunds(any(), anyLong(), any()))
                .thenReturn(PageResult.of(List.of(row), 1L, 1, 10));
        mvc.perform(get("/api/refunds").param("page", "1").param("size", "10")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].refundNo").value("RF1"))
                .andExpect(jsonPath("$.data.list[0].amountYuan").value("50.00"));
    }

    @Test
    @DisplayName("售后详情非本人返回 NOT_FOUND 40401")
    void detailNotFoundReturns40401() throws Exception {
        when(refundQueryService.getDetail(eq("RF1"), anyLong())).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/refunds/RF1")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("未携带 user 令牌访问我的售后返回 40101")
    void listWithoutUserReturns401() throws Exception {
        mvc.perform(get("/api/refunds").param("page", "1").param("size", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private String applyBody(String orderNo, long amountCent, String reason, String proof) throws Exception {
        return toJson(Map.of(
                "orderNo", orderNo,
                "lines", List.of(Map.of("skuId", 1L, "amountCent", amountCent)),
                "reason", reason, "proof", proof));
    }
}
