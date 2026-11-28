package com.sent.sports.web;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customize.controller.CustomOrderUserController;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.service.BatchDeleteCustomOrdersUseCase;
import com.sent.sports.customize.service.CustomQueryService;
import com.sent.sports.customize.service.dto.BatchDeleteCustomOrdersResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockBean
    private BatchDeleteCustomOrdersUseCase batchDeleteUseCase;

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
        when(customQueryService.myOrders(eq(123L), isNull(), any(PageQuery.class)))
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
    @DisplayName("bucket 参数透传给用例（四类筛选由后端 SQL 收窄，不在前端过滤）")
    void passesBucketToUseCase() throws Exception {
        when(customQueryService.myOrders(eq(123L), eq("TODO"), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));

        mvc.perform(get("/api/custom-orders/my")
                        .param("page", "1").param("size", "10").param("bucket", "TODO")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 123L, "jti-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("四类角标：/my/summary 返回四个桶与总数，未登录 40101")
    void returnsSummary() throws Exception {
        when(customQueryService.mySummary(123L))
                .thenReturn(new CustomDtos.MyCustomSummaryVO(2, 1, 3, 4, 12));

        mvc.perform(get("/api/custom-orders/my/summary")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 123L, "jti-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todo").value(2))
                .andExpect(jsonPath("$.data.review").value(1))
                .andExpect(jsonPath("$.data.producing").value(3))
                .andExpect(jsonPath("$.data.done").value(4))
                .andExpect(jsonPath("$.data.total").value(12));

        mvc.perform(get("/api/custom-orders/my/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("缺失令牌按匿名拒绝 40101")
    void anonymousRejected() throws Exception {
        mvc.perform(myWith(null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private MockHttpServletRequestBuilder byOrderWith(String orderNo, CurrentUser user) {
        MockHttpServletRequestBuilder builder = get("/api/custom-orders/by-order/{orderNo}", orderNo);
        if (user != null) {
            builder = builder.requestAttr(CurrentUser.ATTR, user);
        }
        return builder;
    }

    @Test
    @DisplayName("按订单号查定制单：订单详情页据此渲染「上传定制图稿」入口")
    void returnsCustomOrdersOfOrder() throws Exception {
        when(customQueryService.ordersOfOrder(123L, "SO20261121000001")).thenReturn(List.of(
                new CustomDtos.OrderCustomVO("CU1", "WAIT_DRAFT", 0, 0, 30, 300_00L, 1_700_000_000_000L)));

        mvc.perform(byOrderWith("SO20261121000001", new CurrentUser(CurrentUser.AUD_USER, 123L, "jti-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].customOrderId").value("CU1"))
                .andExpect(jsonPath("$.data[0].status").value("WAIT_DRAFT"))
                .andExpect(jsonPath("$.data[0].qty").value(30));
    }

    @Test
    @DisplayName("我的定制多选删除：能删的删掉、进行中的逐条回原因 200")
    void batchDeleteReturnsDeletedAndSkipped() throws Exception {
        when(batchDeleteUseCase.delete(any(), eq(123L))).thenReturn(new BatchDeleteCustomOrdersResult(
                1, List.of(new BatchDeleteCustomOrdersResult.Skipped(
                        "CU2", "定制单还在进行中（生产中），完成或取消后才能删除"))));

        mvc.perform(post("/api/custom-orders/batch-delete")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 123L, "jti-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"CU1\",\"CU2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(1))
                .andExpect(jsonPath("$.data.skipped[0].id").value("CU2"))
                .andExpect(jsonPath("$.data.skipped[0].reason").value(
                        "定制单还在进行中（生产中），完成或取消后才能删除"));
    }

    @Test
    @DisplayName("我的定制多选删除：空选择被校验拦下 400")
    void batchDeleteRejectsEmptySelection() throws Exception {
        mvc.perform(post("/api/custom-orders/batch-delete")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 123L, "jti-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("我的定制多选删除：缺失令牌按匿名拒绝 40101")
    void batchDeleteAnonymousRejected() throws Exception {
        mvc.perform(post("/api/custom-orders/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"CU1\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("我的定制多选删除：管理端令牌也不行（aud 必须是 user），管理端走自己的接口")
    void batchDeleteRejectsAdminToken() throws Exception {
        mvc.perform(post("/api/custom-orders/batch-delete")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 9001L, "jti-9"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"CU1\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        org.mockito.Mockito.verifyNoInteractions(batchDeleteUseCase);
    }

    @Test
    @DisplayName("按订单号查定制单：缺失令牌按匿名拒绝 40101")
    void byOrderAnonymousRejected() throws Exception {
        mvc.perform(byOrderWith("SO20261121000001", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
