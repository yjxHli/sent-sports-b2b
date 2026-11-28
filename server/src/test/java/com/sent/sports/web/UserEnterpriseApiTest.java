package com.sent.sports.web;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.finance.controller.UserEnterpriseController;
import com.sent.sports.finance.dto.UserEnterpriseDtos;
import com.sent.sports.finance.service.UserEnterpriseQueryService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户端企业工具接口契约测试。
 * <p>锁住三件事：客户身份只认令牌（不接受参数）、个人客户回 NONE 不报错、
 * 企业客户的授信与合同按当前客户返回。</p>
 */
@WebMvcTest(UserEnterpriseController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserEnterpriseApiTest extends BaseApiTest {

    private static final CurrentUser USER = new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti-1");

    @MockBean
    private UserEnterpriseQueryService userEnterpriseQueryService;

    private static UserEnterpriseDtos.OverviewVO vo(String status) {
        return new UserEnterpriseDtos.OverviewVO(status, "森特体育用品有限公司", "91320000MA1XXXXX0A",
                200_000L, 50_000L, 150_000L, 30, 1_800_000_000_000L, null, 0L, 1_760_000_000_000L,
                List.of(new UserEnterpriseDtos.ContractItemVO("CT-2026-001", 500_000L, "5000.00",
                        "ACTIVE", "2026-01-01", "2027-01-01", 95)));
    }

    @Test
    @DisplayName("企业客户：返回授信额度、账期与合同列表")
    void overviewReturnsCreditAndContracts() throws Exception {
        when(userEnterpriseQueryService.overview(9002L)).thenReturn(vo("ACTIVE"));

        mvc.perform(get("/api/me/enterprise").requestAttr(CurrentUser.ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.creditAvailableCent").value(150000))
                .andExpect(jsonPath("$.data.paymentTermDays").value(30))
                .andExpect(jsonPath("$.data.contracts[0].contractNo").value("CT-2026-001"));
    }

    @Test
    @DisplayName("个人客户：status=NONE，前端据此渲染「去申请企业认证」")
    void personalCustomerReturnsNone() throws Exception {
        when(userEnterpriseQueryService.overview(9002L)).thenReturn(
                new UserEnterpriseDtos.OverviewVO("NONE", null, null, 0L, 0L, 0L, 0, 0L,
                        null, 0L, 0L, List.of()));

        mvc.perform(get("/api/me/enterprise").requestAttr(CurrentUser.ATTR, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NONE"))
                .andExpect(jsonPath("$.data.creditLimitCent").value(0))
                .andExpect(jsonPath("$.data.contracts").isEmpty());
    }

    @Test
    @DisplayName("未登录返回 40101，且不触达用例")
    void withoutUserReturns40101() throws Exception {
        mvc.perform(get("/api/me/enterprise"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        verify(userEnterpriseQueryService, never()).overview(anyLong());
    }

    @Test
    @DisplayName("管理端令牌（aud=admin）不能查客户企业档案")
    void adminAudienceRejected() throws Exception {
        mvc.perform(get("/api/me/enterprise")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 9001L, "jti-2")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        verify(userEnterpriseQueryService, never()).overview(anyLong());
    }
}
