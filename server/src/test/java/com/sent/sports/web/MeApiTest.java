package com.sent.sports.web;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.customer.controller.user.MeController;
import com.sent.sports.customer.dto.user.MeVO;
import com.sent.sports.customer.service.CustomerProfileQueryService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户端个人资料接口契约测试（收口阶段）：{@code GET /api/me}。
 * 携带 user 端令牌属性返回 200 与资料；缺失/非 user 令牌按匿名拒绝 40101。
 */
@WebMvcTest(MeController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeApiTest extends BaseApiTest {

    @MockBean
    private CustomerProfileQueryService profileQueryService;

    private MockHttpServletRequestBuilder meWith(CurrentUser user) {
        MockHttpServletRequestBuilder builder = get("/api/me");
        if (user != null) {
            builder = builder.requestAttr(CurrentUser.ATTR, user);
        }
        return builder;
    }

    @Test
    @DisplayName("携带 user 端令牌返回个人资料 200")
    void returnsProfileForUser() throws Exception {
        when(profileQueryService.profile(123L)).thenReturn(
                new MeVO(123L, "C1001", "小明", "13800000000", "m@x.com",
                        "PERSON", "LV2", 120L, 880L, "ACTIVE"));

        mvc.perform(meWith(new CurrentUser(CurrentUser.AUD_USER, 123L, "jti-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerId").value(123))
                .andExpect(jsonPath("$.data.customerNo").value("C1001"))
                .andExpect(jsonPath("$.data.nickname").value("小明"))
                .andExpect(jsonPath("$.data.type").value("PERSON"))
                .andExpect(jsonPath("$.data.levelCode").value("LV2"))
                .andExpect(jsonPath("$.data.growthValue").value(120))
                .andExpect(jsonPath("$.data.pointsBalance").value(880))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("缺失令牌按匿名拒绝 40101")
    void anonymousRejected() throws Exception {
        mvc.perform(meWith(null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("admin 令牌访问 /api/me 同样拒绝 40101")
    void adminTokenRejected() throws Exception {
        mvc.perform(meWith(new CurrentUser(CurrentUser.AUD_ADMIN, 7L, "jti-2")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("客户不存在返回 NOT_FOUND 40401")
    void notFoundReturns40401() throws Exception {
        when(profileQueryService.profile(999L))
                .thenThrow(new com.sent.sports.common.error.BizException(
                        com.sent.sports.common.error.ErrorCode.NOT_FOUND));

        mvc.perform(meWith(new CurrentUser(CurrentUser.AUD_USER, 999L, "jti-3")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
