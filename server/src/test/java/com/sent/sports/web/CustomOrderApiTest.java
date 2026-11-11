package com.sent.sports.web;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customize.controller.CustomOrderUserController;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.service.CustomQueryService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户端「我的定制单」接口契约测试（收口阶段）：{@code GET /api/custom-orders/my}。
 * 携带 user 端令牌返回分页列表（含订单状态与图稿版本）；缺失/非 user 令牌按匿名拒绝 40101。
 */
@WebMvcTest(CustomOrderUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomOrderApiTest extends BaseApiTest {

    @MockBean
    private CustomQueryService customQueryService;

    private MockHttpServletRequestBuilder myWith(CurrentUser user) {
        MockHttpServletRequestBuilder builder = get("/api/custom-orders/my").param("page", "1").param("size", "10");
        if (user != null) {
            builder = builder.requestAttr(CurrentUser.ATTR, user);
        }
        return builder;
    }

    @Test
    @DisplayName("携带 user 端令牌返回我的定制单分页 200")
    void returnsMyOrders() throws Exception {
        CustomDtos.MyCustomOrderVO vo = new CustomDtos.MyCustomOrderVO(
                "CO1", "WAIT_REVIEW", "PAID", 2, 0, 1_700_000_000_000L);
        when(customQueryService.myOrders(eq(123L), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(vo), 1L, 1, 10));

        mvc.perform(myWith(new CurrentUser(CurrentUser.AUD_USER, 123L, "jti-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].customOrderId").value("CO1"))
                .andExpect(jsonPath("$.data.list[0].status").value("WAIT_REVIEW"))
                .andExpect(jsonPath("$.data.list[0].orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.list[0].version").value(2))
                .andExpect(jsonPath("$.data.list[0].rejectCount").value(0));
    }

    @Test
    @DisplayName("缺失令牌按匿名拒绝 40101")
    void anonymousRejected() throws Exception {
        mvc.perform(myWith(null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
