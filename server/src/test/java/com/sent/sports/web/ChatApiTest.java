package com.sent.sports.web;

import com.sent.sports.chat.controller.ChatController;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.domain.SessionType;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.CreateSessionUseCase;
import com.sent.sports.chat.service.RateSatisfactionUseCase;
import com.sent.sports.chat.service.SendMessageUseCase;
import com.sent.sports.chat.service.dto.CreateSessionCmd;
import com.sent.sports.chat.service.dto.CreateSessionResult;
import com.sent.sports.chat.service.dto.SendMessageCmd;
import com.sent.sports.chat.service.dto.SendMessageResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
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

    @MockBean
    private RateSatisfactionUseCase rateSatisfactionUseCase;

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
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sessionType").value("HUMAN"));
    }

    @Test
    @DisplayName("不传 sessionType 按人工建会话（既有接口口径不能被机器人改掉）")
    void createSessionDefaultsToHuman() throws Exception {
        when(createSessionUseCase.create(any()))
                .thenReturn(new CreateSessionResult(101L, ChatSessionStatus.WAITING, null, false));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);

        mvc.perform(post("/api/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(jsonPath("$.data.sessionType").value("HUMAN"));

        ArgumentCaptor<CreateSessionCmd> captor = ArgumentCaptor.forClass(CreateSessionCmd.class);
        verify(createSessionUseCase).create(captor.capture());
        assertEquals(SessionType.HUMAN, captor.getValue().sessionType());
    }

    @Test
    @DisplayName("显式传 BOT：透传到用例，响应回 BOT 供前端判断输入框是否可用")
    void createSessionWithBotType() throws Exception {
        when(createSessionUseCase.create(any()))
                .thenReturn(new CreateSessionResult(102L, ChatSessionStatus.WAITING, null, false,
                        SessionType.BOT));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
        body.put("sessionType", "BOT");

        mvc.perform(post("/api/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(jsonPath("$.data.sessionType").value("BOT"))
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        ArgumentCaptor<CreateSessionCmd> captor = ArgumentCaptor.forClass(CreateSessionCmd.class);
        verify(createSessionUseCase).create(captor.capture());
        assertEquals(SessionType.BOT, captor.getValue().sessionType());
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
                new ChatDtos.MessageVO("CM1", 100L, "CUSTOMER", "TEXT", "你好", "c-1", 7, 1_700_000_000_000L)));
        mvc.perform(get("/api/chat/sessions/100/messages").param("afterSeq", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].messageId").value("CM1"))
                .andExpect(jsonPath("$.data[0].msgType").value("TEXT"))
                .andExpect(jsonPath("$.data[0].seq").value(7));
    }

    @Test
    @DisplayName("发图片消息：msgType=IMAGE 透传到用例，历史里也能读回 IMAGE")
    void sendImageMessageCarriesMsgType() throws Exception {
        when(sendMessageUseCase.send(any())).thenReturn(new SendMessageResult("CM9", 3, 100L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("senderType", "CUSTOMER");
        body.put("content", "2026/11/ab/shot.png");
        body.put("clientMsgId", "c-img");
        body.put("msgType", "IMAGE");
        mvc.perform(post("/api/chat/sessions/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.msgType").value("IMAGE"));

        ArgumentCaptor<SendMessageCmd> captor = ArgumentCaptor.forClass(SendMessageCmd.class);
        verify(sendMessageUseCase).send(captor.capture());
        assertEquals("IMAGE", captor.getValue().msgType());
        assertEquals("2026/11/ab/shot.png", captor.getValue().content());
    }

    @Test
    @DisplayName("历史咨询会话列表：按客户返回会话摘要 200")
    void listSessionsReturns200() throws Exception {
        when(chatQueryService.listSessions(9002L, 20)).thenReturn(List.of(
                new ChatDtos.SessionSummaryVO(9L, "CLOSED", 9003L, 1_700_000_000_000L, 1_700_000_100_000L)));

        mvc.perform(get("/api/chat/sessions").param("customerId", "9002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].sessionId").value(9))
                .andExpect(jsonPath("$.data[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.data[0].staffId").value(9003));
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
