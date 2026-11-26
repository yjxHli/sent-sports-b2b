package com.sent.sports.web;

import com.sent.sports.chat.controller.admin.AdminFaqController;
import com.sent.sports.chat.dto.admin.AdminFaqDtos;
import com.sent.sports.chat.service.FaqAdminService;
import com.sent.sports.common.web.PageResult;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 话术配置接口契约测试（V60，TDD 红）。
 *
 * <p>只验「请求 → 控制器 → 用例 → 响应」的形状：筛选参数透传、新增返回落库后的 VO、
 * 启停走独立子路径。权限（{@code @RequirePerm(exact = true)}）由 E2E 用真实令牌覆盖——
 * {@link BaseApiTest} 关闭了过滤器链，切片测试里 @RequirePerm 不生效。</p>
 */
@WebMvcTest(AdminFaqController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminFaqApiTest extends BaseApiTest {

    @MockBean
    private FaqAdminService faqAdminService;

    private AdminFaqDtos.FaqVO vo(long id, String keywords, String reply, String status) {
        return new AdminFaqDtos.FaqVO(id, keywords, reply, 3, status, 1_800_000_000_000L);
    }

    @Test
    @DisplayName("分页查询：关键词与状态透传，行内字段完整")
    void listPassesFilters() throws Exception {
        when(faqAdminService.list(eq("退换"), eq("ENABLED"), any()))
                .thenReturn(PageResult.of(List.of(vo(12L, "退换货", "支持7天无理由", "ENABLED")), 1, 1, 20));

        mvc.perform(get("/api/admin/faq")
                        .param("keyword", "退换")
                        .param("status", "ENABLED")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(12))
                .andExpect(jsonPath("$.data.list[0].keywords").value("退换货"))
                .andExpect(jsonPath("$.data.list[0].hitCount").value(3))
                .andExpect(jsonPath("$.data.list[0].status").value("ENABLED"));
    }

    @Test
    @DisplayName("新增：请求体原样交给用例，返回新条目")
    void createReturnsVo() throws Exception {
        when(faqAdminService.create(any(), any())).thenReturn(vo(12L, "退换货", "支持7天无理由", "ENABLED"));

        mvc.perform(post("/api/admin/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AdminFaqDtos.FaqCmd("退换货", "支持7天无理由", "ENABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.replyContent").value("支持7天无理由"));

        verify(faqAdminService).create(
                eq(new AdminFaqDtos.FaqCmd("退换货", "支持7天无理由", "ENABLED")), isNull());
    }

    @Test
    @DisplayName("修改：路径 id 进用例，空关键词被 Bean Validation 拦下")
    void updateAndValidate() throws Exception {
        when(faqAdminService.update(eq(12L), any(), any()))
                .thenReturn(vo(12L, "退换货", "支持7天无理由", "ENABLED"));

        mvc.perform(put("/api/admin/faq/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AdminFaqDtos.FaqCmd("退换货", "支持7天无理由", "ENABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12));

        mvc.perform(put("/api/admin/faq/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AdminFaqDtos.FaqCmd("", "支持7天无理由", "ENABLED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("启停：走独立子路径，状态原样下发")
    void updateStatus() throws Exception {
        when(faqAdminService.updateStatus(eq(12L), eq("DISABLED"), any()))
                .thenReturn(vo(12L, "退换货", "支持7天无理由", "DISABLED"));

        mvc.perform(put("/api/admin/faq/12/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AdminFaqDtos.StatusCmd("DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(faqAdminService).updateStatus(eq(12L), eq("DISABLED"), any());
    }

    @Test
    @DisplayName("批量删除：ids 原样交给用例，返回实际删除条数")
    void batchDeleteReturnsDeletedCount() throws Exception {
        when(faqAdminService.delete(anyList(), any())).thenReturn(2);

        mvc.perform(post("/api/admin/faq/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("ids", List.of("12", "13")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(2));

        verify(faqAdminService).delete(eq(List.of("12", "13")), isNull());
    }

    @Test
    @DisplayName("批量删除：空 ids 被 Bean Validation 拦下（400），不下发用例")
    void batchDeleteRejectsEmptyIds() throws Exception {
        mvc.perform(post("/api/admin/faq/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("ids", List.of()))))
                .andExpect(status().isBadRequest());

        verify(faqAdminService, never()).delete(anyList(), any());
    }
}
