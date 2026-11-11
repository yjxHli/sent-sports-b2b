package com.sent.sports.job;

import com.sent.sports.chat.ws.SessionBroadcaster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** WebSocket 心跳回收任务单测（P5-34）。 */
class WsHeartbeatJobTest {

    @Test
    void runOnceDelegatesIdleTimeout() {
        SessionBroadcaster broadcaster = mock(SessionBroadcaster.class);
        when(broadcaster.closeIdle(300_000L)).thenReturn(3);

        assertEquals(3, new WsHeartbeatJob(broadcaster, 300_000L).runOnce());
        verify(broadcaster).closeIdle(300_000L);
    }

    @Test
    void scheduledToleratesZeroClosed() {
        SessionBroadcaster broadcaster = mock(SessionBroadcaster.class);
        when(broadcaster.closeIdle(1_000L)).thenReturn(0);

        WsHeartbeatJob job = new WsHeartbeatJob(broadcaster, 1_000L);
        job.scheduled();

        verify(broadcaster).closeIdle(1_000L);
    }
}
