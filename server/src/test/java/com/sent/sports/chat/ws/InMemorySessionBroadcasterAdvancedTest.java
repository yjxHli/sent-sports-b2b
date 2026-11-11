package com.sent.sports.chat.ws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 广播器进阶单测（P5-36）：多端并存、精确注销、心跳回收。 */
@ExtendWith(MockitoExtension.class)
class InMemorySessionBroadcasterAdvancedTest {

    @Mock
    private WebSocketSession firstDevice;
    @Mock
    private WebSocketSession secondDevice;

    @Test
    void sameStaffMultipleDevicesAllReceive() throws Exception {
        InMemorySessionBroadcaster broadcaster = new InMemorySessionBroadcaster();
        when(firstDevice.isOpen()).thenReturn(true);
        when(secondDevice.isOpen()).thenReturn(true);
        broadcaster.register(7L, firstDevice);
        broadcaster.register(7L, secondDevice);

        assertEquals(2, broadcaster.onlineCount());
        broadcaster.broadcast(7L, WsEnvelope.msg(10L, 1L, "hi"));

        verify(firstDevice).sendMessage(any(TextMessage.class));
        verify(secondDevice).sendMessage(any(TextMessage.class));
    }

    @Test
    void preciseUnregisterKeepsOtherDevice() {
        InMemorySessionBroadcaster broadcaster = new InMemorySessionBroadcaster();
        broadcaster.register(7L, firstDevice);
        broadcaster.register(7L, secondDevice);

        broadcaster.unregister(7L, firstDevice);

        assertEquals(1, broadcaster.onlineCount());
    }

    @Test
    void unregisterAllRemovesEveryDevice() {
        InMemorySessionBroadcaster broadcaster = new InMemorySessionBroadcaster();
        broadcaster.register(7L, firstDevice);
        broadcaster.register(7L, secondDevice);

        broadcaster.unregister(7L);

        assertEquals(0, broadcaster.onlineCount());
    }

    @Test
    void broadcastAllReachesEveryOnlineDevice() throws Exception {
        InMemorySessionBroadcaster broadcaster = new InMemorySessionBroadcaster();
        when(firstDevice.isOpen()).thenReturn(true);
        when(secondDevice.isOpen()).thenReturn(true);
        broadcaster.register(7L, firstDevice);
        broadcaster.register(8L, secondDevice);

        broadcaster.broadcastAll(WsEnvelope.queue(0L, 0L, "new session"));

        verify(firstDevice).sendMessage(any(TextMessage.class));
        verify(secondDevice).sendMessage(any(TextMessage.class));
    }

    @Test
    void closeIdleClosesOnlyStaleDevices() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        InMemorySessionBroadcaster broadcaster = new InMemorySessionBroadcaster(clock);
        when(firstDevice.isOpen()).thenReturn(true);
        broadcaster.register(7L, firstDevice);
        clock.setMillis(1_030_000L);
        broadcaster.register(8L, secondDevice);
        clock.setMillis(1_080_000L);
        broadcaster.touch(8L, secondDevice);
        clock.setMillis(1_090_000L);

        assertEquals(1, broadcaster.closeIdle(30_000L));
        assertEquals(1, broadcaster.onlineCount());
        verify(firstDevice).close(any(CloseStatus.class));
        verify(secondDevice, never()).close(any(CloseStatus.class));
    }

    /** 可推进的测试时钟。 */
    private static final class MutableClock extends Clock {

        private volatile long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void setMillis(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
