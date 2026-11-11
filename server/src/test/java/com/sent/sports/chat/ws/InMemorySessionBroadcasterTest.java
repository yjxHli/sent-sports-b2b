package com.sent.sports.chat.ws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 进程内广播器单元测试（P5-36）。 */
@ExtendWith(MockitoExtension.class)
class InMemorySessionBroadcasterTest {

    @Mock
    private WebSocketSession openSession;

    @Mock
    private WebSocketSession closedSession;

    private final SessionBroadcaster broadcaster = new InMemorySessionBroadcaster();

    @Test
    void registerUnregisterAndOnlineCount() {
        assertThat(broadcaster.onlineCount()).isZero();

        broadcaster.register(1L, openSession);
        broadcaster.register(2L, closedSession);
        assertThat(broadcaster.onlineCount()).isEqualTo(2);

        broadcaster.unregister(1L);
        assertThat(broadcaster.onlineCount()).isEqualTo(1);

        broadcaster.unregister(2L);
        assertThat(broadcaster.onlineCount()).isZero();
    }

    @Test
    void broadcastSendsExactlyOnceToOpenSession() throws Exception {
        when(openSession.isOpen()).thenReturn(true);
        broadcaster.register(1L, openSession);

        broadcaster.broadcast(1L, WsEnvelope.msg(10L, 1L, "hi"));

        verify(openSession, times(1)).isOpen();
        verify(openSession, times(1)).sendMessage(any(TextMessage.class));
        verifyNoMoreInteractions(openSession);
    }

    @Test
    void closedSessionIsNotSentAndDoesNotThrow() throws Exception {
        when(closedSession.isOpen()).thenReturn(false);
        broadcaster.register(2L, closedSession);

        assertThatCode(() -> broadcaster.broadcast(2L, WsEnvelope.msg(10L, 1L, "hi")))
                .doesNotThrowAnyException();

        verify(closedSession, times(1)).isOpen();
        verify(closedSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastToUnregisteredStaffDoesNothing() {
        assertThatCode(() -> broadcaster.broadcast(999L, WsEnvelope.msg(1L, 1L, "x")))
                .doesNotThrowAnyException();

        verifyNoInteractions(openSession, closedSession);
    }

    @Test
    void sendFailureIsLoggedAndNotPropagated() throws Exception {
        when(openSession.isOpen()).thenReturn(true);
        doThrow(new IOException("boom")).when(openSession).sendMessage(any(TextMessage.class));
        broadcaster.register(1L, openSession);

        assertThatCode(() -> broadcaster.broadcast(1L, WsEnvelope.msg(1L, 1L, "x")))
                .doesNotThrowAnyException();

        verify(openSession, times(1)).sendMessage(any(TextMessage.class));
    }
}
