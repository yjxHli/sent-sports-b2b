package com.sent.sports.web.admin;

import com.sent.sports.common.web.R;
import com.sent.sports.customer.controller.admin.AdminCustomerTagController;
import com.sent.sports.customer.dto.admin.CustomerTagDtos;
import com.sent.sports.customer.service.CustomerTagService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户标签接口契约测试（TDD 红）。
 *
 * <p>三条路径各有不同受众，别混：</p>
 * <ul>
 *   <li>{@code GET /admin/customer-tags}：客服弹窗 + 管理端维护页共用同一份目录；</li>
 *   <li>{@code GET/PUT /admin/customers/{id}/tags}：客服在会话头部打标 / 取消，全量提交；</li>
 *   <li>{@code POST/PUT/DELETE /admin/customer-tags}：只有后台人员能改「有哪些标签」。</li>
 * </ul>
 * <p>权限（{@code chat:serve} / {@code customer:tag:manage}）由 E2E 用真实令牌覆盖，
 * 切片测试关掉了过滤器链。</p>
 */
@WebMvcTest(AdminCustomerTagController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCustomerTagApiTest extends BaseApiTest {

    @MockBean
    private CustomerTagService customerTagService;

    private CustomerTagDtos.TagVO vo(long id, String name, String category) {
        return new CustomerTagDtos.TagVO(id, name, category, "优质 / 正面", "excellent", "ACTIVE", (int) id);
    }

    @Test
    @DisplayName("目录：includeDisabled 透传，行内带中文分类与配色键")
    void catalogPassesFlag() throws Exception {
        when(customerTagService.catalog(true)).thenReturn(List.of(vo(1L, "忠实老客", "EXCELLENT")));

        mvc.perform(get("/api/admin/customer-tags").param("includeDisabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("忠实老客"))
                .andExpect(jsonPath("$.data[0].categoryLabel").value("优质 / 正面"))
                .andExpect(jsonPath("$.data[0].colorKey").value("excellent"));

        verify(customerTagService).catalog(true);
    }

    @Test
    @DisplayName("目录：不带参数默认只给启用中的标签")
    void catalogDefaultsToEnabledOnly() throws Exception {
        when(customerTagService.catalog(false)).thenReturn(List.of());

        mvc.perform(get("/api/admin/customer-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(customerTagService).catalog(false);
    }

    @Test
    @DisplayName("某客户的标签：GET 返回清单，PUT 全量提交勾选")
    void customerTagsGetAndApply() throws Exception {
        when(customerTagService.tagsOf(9002L)).thenReturn(List.of(vo(1L, "忠实老客", "EXCELLENT")));
        when(customerTagService.applyTags(eq(9002L), eq(List.of(1L, 3L)), any()))
                .thenReturn(List.of(vo(1L, "忠实老客", "EXCELLENT"), vo(3L, "价格敏感", "NEUTRAL")));

        mvc.perform(get("/api/admin/customers/9002/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("忠实老客"));

        mvc.perform(put("/api/admin/customers/9002/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("tagIds", List.of(1, 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].name").value("价格敏感"));

        verify(customerTagService).applyTags(eq(9002L), eq(List.of(1L, 3L)), isNull());
    }

    @Test
    @DisplayName("某客户的标签：tagIds 缺失被 Bean Validation 拦下（400）")
    void applyTagsRequiresTagIds() throws Exception {
        mvc.perform(put("/api/admin/customers/9002/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("维护：新增 / 改名 / 停用 / 删除四条写路径")
    void maintainTags() throws Exception {
        when(customerTagService.create(any(), any())).thenReturn(vo(88L, "新标签", "RISK"));
        when(customerTagService.update(eq(88L), any(), any())).thenReturn(vo(88L, "改名后", "RISK"));
        when(customerTagService.setStatus(eq(88L), eq("DISABLED"), any())).thenReturn(vo(88L, "改名后", "RISK"));

        mvc.perform(post("/api/admin/customer-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "新标签", "category", "RISK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagId").value(88));

        mvc.perform(put("/api/admin/customer-tags/88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "改名后", "category", "RISK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名后"));

        mvc.perform(put("/api/admin/customer-tags/88/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/admin/customer-tags/88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(customerTagService).delete(eq(88L), isNull());
    }

    @Test
    @DisplayName("维护：空标签名 400，不下发服务层")
    void createRejectsBlankName() throws Exception {
        mvc.perform(post("/api/admin/customer-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "  ", "category", "RISK"))))
                .andExpect(status().isBadRequest());

        verify(customerTagService, org.mockito.Mockito.never()).create(any(), any());
    }
}
