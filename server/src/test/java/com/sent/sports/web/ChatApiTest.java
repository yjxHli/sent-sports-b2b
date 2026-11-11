package com.sent.sports.web;

import com.sent.sports.chat.controller.ChatController;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.CreateSessionUseCase;
import com.sent.sports.chat.service.SendMessageUseCase;
import com.sent.sports.chat.service.dto.CreateSessionResult;
import com.sent.sports.chat.service.dto.SendMessageResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客服会话接口契约测试（P4-29，红→绿）。
 * 用户侧：建会话、发消息、afterSeq 补拉、已读上报。分配/关闭/在线状态属管理侧（AssignSession / CloseSession /
 * Presence），本期不做用户侧契约。
 */
@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatApiTest extends BaseApiTest {

    @MockBean
    private CreateSessionUseCase createSessionUseCase;

    @MockBean
    private SendMessageUseCase sendMessageUseCase;

    @MockBean
    private ChatQueryService chatQueryService;

    @Test
    @DisplayName("发起会话返回会话号与状态 200")
    void createSessionReturns200() throws Exception {
        when(createSessionUseCase.create(any()))
                .thenReturn(new CreateSessionResult(100L, ChatSessionStatus.ACTIVE, 5L, false));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
        body.put("vip", false);
        mvc.perform(post("/api/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(100))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("发送消息返回序号 200")
    void sendMessageReturns200() throws Exception {
        when(sendMessageUseCase.send(any()))
                .thenReturn(new SendMessageResult("CM1", 7, 100L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("senderType", "CUSTOMER");
        body.put("content", "你好");
        body.put("clientMsgId", "c-1");
        mvc.perform(post("/api/chat/sessions/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.messageId").value("CM1"))
                .andExpect(jsonPath("$.data.seq").value(7));
    }

    @Test
    @DisplayName("afterSeq 补拉历史消息 200")
    void historyAfterSeqReturns200() throws Exception {
        when(chatQueryService.history(anyLong(), anyInt())).thenReturn(List.of(
                new ChatDtos.MessageVO("CM1", 100L, "CUSTOMER", "你好", "c-1", 7, 1_700_000_000_000L)));
        mvc.perform(get("/api/chat/sessions/100/messages").param("afterSeq", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].messageId").value("CM1"))
                .andExpect(jsonPath("$.data[0].seq").value(7));
    }

    @Test
    @DisplayName("已读上报 200")
    void reportReadReturns200() throws Exception {
        doNothing().when(chatQueryService).reportRead(anyLong(), anyInt());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("seq", 7);
        mvc.perform(post("/api/chat/sessions/100/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
