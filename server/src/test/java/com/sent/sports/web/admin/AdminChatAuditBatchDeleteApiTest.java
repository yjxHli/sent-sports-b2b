package com.sent.sports.web.admin;

import com.sent.sports.chat.controller.admin.AdminChatAuditBatchDeleteController;
import com.sent.sports.chat.service.ChatAuditBatchDeleteUseCase;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.web.ConfirmBodyCacheFilter;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 客服审查批量删除接口契约：确认令牌口径与发票管理完全一致。 */
@WebMvcTest(AdminChatAuditBatchDeleteController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminChatAuditBatchDeleteApiTest extends BaseApiTest {

    @MockBean
    private ConfirmTokenService confirmTokenService;

    @MockBean
    private ChatAuditBatchDeleteUseCase deleteUseCase;

    @Test
    @DisplayName("正确确认令牌：返回实际软删条数")
    void validTokenDeletes() throws Exception {
        when(confirmTokenService.consume(ConfirmAction.BATCH_DELETE.name(), "101,102", "tk-ok"))
                .thenReturn(true);
        when(deleteUseCase.delete(List.of("102", "101"))).thenReturn(2);

        String body = toJson(Map.of("ids", List.of("102", "101")));
        mvc.perform(post("/api/admin/chat/audit/sessions/batch-delete")
                        .header("X-Confirm-Token", "tk-ok")
                        // 拦截器在参数绑定前算目标，依赖请求体预读缓存（生产由 ConfirmBodyCacheFilter 填充）
                        .requestAttr(ConfirmBodyCacheFilter.BODY_ATTR,
                                body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(2));
        verify(deleteUseCase).delete(List.of("102", "101"));
    }

    @Test
    @DisplayName("缺少确认令牌返回 40302")
    void missingTokenRejected() throws Exception {
        mvc.perform(post("/api/admin/chat/audit/sessions/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("ids", List.of("101")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }
}
