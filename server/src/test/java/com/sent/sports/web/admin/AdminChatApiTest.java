package com.sent.sports.web.admin;

import com.sent.sports.chat.controller.admin.AdminChatController;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.service.AssignSessionUseCase;
import com.sent.sports.chat.service.ChatAdminQueryService;
import com.sent.sports.chat.service.CloseSessionUseCase;
import com.sent.sports.chat.service.PresenceUseCase;
import com.sent.sports.chat.service.dto.AssignSessionResult;
import com.sent.sports.chat.service.dto.PresenceResult;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客服工作台接口契约测试（P4-50，红→绿）。
 * 在线状态切换 200、转接缺原因 40001、结束会话查无单 40401、接入 200、三态列表空页 200。
 */
@WebMvcTest(AdminChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminChatApiTest extends BaseApiTest {

    @MockBean
    private AssignSessionUseCase assignSessionUseCase;

    @MockBean
    private CloseSessionUseCase closeSessionUseCase;

    @MockBean
    private PresenceUseCase presenceUseCase;

    @MockBean
    private ChatAdminQueryService chatAdminQueryService;

    @Test
    @DisplayName("切换在线状态成功返回状态 200")
    void presenceReturns200() throws Exception {
        when(presenceUseCase.setPresence(any())).thenReturn(new PresenceResult(1L, PresenceStatus.ONLINE, 0));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("staffId", 1L);
        body.put("presence", "ONLINE");
        mvc.perform(post("/api/admin/chat/presence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.staffId").value(1))
                .andExpect(jsonPath("$.data.presence").value("ONLINE"))
                .andExpect(jsonPath("$.data.releasedSessions").value(0));
    }

    @Test
    @DisplayName("转接缺少原因返回 40001")
    void transferMissingReasonReturns40001() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("toStaffId", 2L);
        mvc.perform(post("/api/admin/chat/sessions/10/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("结束会话查无单返回 NOT_FOUND 40401")
    void closeNotFoundReturns40401() throws Exception {
        when(closeSessionUseCase.close(any())).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "DONE");
        mvc.perform(post("/api/admin/chat/sessions/404/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("接入会话成功返回状态与接待客服 200")
    void acceptReturns200() throws Exception {
        when(assignSessionUseCase.assign(any())).thenReturn(
                new AssignSessionResult(10L, ChatSessionStatus.ACTIVE, 1L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("staffId", 1L);
        mvc.perform(post("/api/admin/chat/sessions/10/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(10))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.staffId").value(1));
    }

    @Test
    @DisplayName("三态会话列表返回空分页 200")
    void sessionsReturnsEmptyPage() throws Exception {
        when(chatAdminQueryService.sessions(any())).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/chat/sessions").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
