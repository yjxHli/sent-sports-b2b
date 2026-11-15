package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.finance.controller.UserInvoiceController;
import com.sent.sports.finance.dto.UserInvoiceDtos;
import com.sent.sports.finance.service.UserInvoiceService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户端开票接口契约测试。
 * <p>锁住 4 件事：
 * <ul>
 *   <li>申请成功回 200 并带出状态与票面金额；</li>
 *   <li>抬头缺失回 40001（{@code @Valid} 生效）；</li>
 *   <li>未支付回 40901（状态冲突，前端据此提示「支付后可开票」）；</li>
 *   <li>客户身份取自请求属性，控制器不接受请求体传 customerId，且必须 aud=user。</li>
 * </ul>
 */
@WebMvcTest(UserInvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserInvoiceApiTest extends BaseApiTest {

    private static final CurrentUser USER = new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti-1");

    @MockBean
    private UserInvoiceService userInvoiceService;

    private static UserInvoiceDtos.InvoiceVO vo(String status) {
        return new UserInvoiceDtos.InvoiceVO("31", "SO20261115000001", status,
                "森特体育用品有限公司", null, "NORMAL", "fin@sent.com",
                128_800L, "1288.00", null, null, 1_763_000_000_000L);
    }

    @Test
    @DisplayName("提交开票申请返回 200 与申请状态")
    void applyReturns200() throws Exception {
        when(userInvoiceService.apply(eq(9002L), any())).thenReturn(vo("APPLIED"));

        mvc.perform(post("/api/invoices")
                        .requestAttr(CurrentUser.ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("orderNo", "SO20261115000001",
                                "title", "森特体育用品有限公司", "type", "NORMAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.amountYuan").value("1288.00"));
    }

    @Test
    @DisplayName("抬头缺失返回 40001")
    void applyWithoutTitleReturns40001() throws Exception {
        mvc.perform(post("/api/invoices")
                        .requestAttr(CurrentUser.ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("orderNo", "SO20261115000001"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("订单未支付返回 40901")
    void applyUnpaidReturns40901() throws Exception {
        when(userInvoiceService.apply(eq(9002L), any()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT, "订单支付完成后才能申请开票"));

        mvc.perform(post("/api/invoices")
                        .requestAttr(CurrentUser.ATTR, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("orderNo", "SO20261115000001",
                                "title", "森特体育用品有限公司"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("查开票状态返回最新申请")
    void latestReturns200() throws Exception {
        when(userInvoiceService.latest(9002L, "SO20261115000001")).thenReturn(vo("ISSUED"));

        mvc.perform(get("/api/invoices/order/SO20261115000001")
                        .requestAttr(CurrentUser.ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }

    @Test
    @DisplayName("从未申请时 data 为 null，前端据此继续显示「申请开票」")
    void latestWithoutApplicationReturnsNullData() throws Exception {
        when(userInvoiceService.latest(9002L, "SO20261115000001")).thenReturn(null);

        mvc.perform(get("/api/invoices/order/SO20261115000001")
                        .requestAttr(CurrentUser.ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("无客户身份返回 40101，且不触达用例")
    void applyWithoutUserReturns40101() throws Exception {
        mvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("orderNo", "SO20261115000001",
                                "title", "森特体育用品有限公司"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("管理端令牌（aud=admin）不能走客户开票接口")
    void adminAudienceRejected() throws Exception {
        mvc.perform(post("/api/invoices")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 9001L, "jti-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("orderNo", "SO20261115000001",
                                "title", "森特体育用品有限公司"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        verify(userInvoiceService, never()).apply(anyLong(), any());
    }
}
