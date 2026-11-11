package com.sent.sports.chat.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 进程内会话广播实现（P5-36）：{@code staffId -> Set<WebSocketSession>} 支持一个客服多端在线。
 * <p>三条稳健性约定：
 * <ul>
 *   <li>发送时对目标 session 加 synchronized 锁，避免同一会话并发写帧导致协议错乱；</li>
 *   <li>仅向 {@code isOpen()} 的会话发送，异常只记日志不抛出，坏连接不影响调用方；</li>
 *   <li>{@code lastActive} 记录上行活跃时间，由定时任务调用 {@link #closeIdle(long)} 清理僵死连接，
 *       不依赖浏览器一定发 PING/PONG。</li>
 * </ul>
 */
@Component
public class InMemorySessionBroadcaster implements SessionBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionBroadcaster.class);

    private final Map<Long, Set<WebSocketSession>> staffSessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> customerSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Long> lastActive = new ConcurrentHashMap<>();
    private final Clock clock;

    /** 单测/直接使用时的默认时钟。 */
    public InMemorySessionBroadcaster() {
        this(Clock.systemUTC());
    }

    @Autowired
    public InMemorySessionBroadcaster(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void register(long staffId, WebSocketSession session) {
        staffSessions.computeIfAbsent(staffId, key -> new CopyOnWriteArraySet<>()).add(session);
        lastActive.put(session, now());
    }

    @Override
    public void unregister(long staffId) {
        Set<WebSocketSession> removed = staffSessions.remove(staffId);
        if (removed != null) {
            removed.forEach(lastActive::remove);
        }
    }

    @Override
    public void unregister(long staffId, WebSocketSession session) {
        Set<WebSocketSession> set = staffSessions.get(staffId);
        if (set == null) {
            return;
        }
        set.remove(session);
        lastActive.remove(session);
        if (set.isEmpty()) {
            staffSessions.remove(staffId, set);
        }
    }

    @Override
    public void registerCustomer(long customerId, WebSocketSession session) {
        customerSessions.computeIfAbsent(customerId, key -> new CopyOnWriteArraySet<>()).add(session);
        lastActive.put(session, now());
    }

    @Override
    public void unregisterCustomer(long customerId, WebSocketSession session) {
        Set<WebSocketSession> set = customerSessions.get(customerId);
        if (set == null) {
            return;
        }
        set.remove(session);
        lastActive.remove(session);
        if (set.isEmpty()) {
            customerSessions.remove(customerId, set);
        }
    }

    @Override
    public void touch(long staffId, WebSocketSession session) {
        lastActive.computeIfPresent(session, (key, previous) -> now());
    }

    @Override
    public void touchCustomer(long customerId, WebSocketSession session) {
        lastActive.computeIfPresent(session, (key, previous) -> now());
    }

    @Override
    public void broadcast(long staffId, WsEnvelope envelope) {
        Set<WebSocketSession> targets = staffSessions.get(staffId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        String payload = envelope.toJson();
        for (WebSocketSession session : targets) {
            sendQuietly(staffId, session, payload);
        }
    }

    @Override
    public void broadcastToCustomer(long customerId, WsEnvelope envelope) {
        Set<WebSocketSession> targets = customerSessions.get(customerId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        String payload = envelope.toJson();
        for (WebSocketSession session : targets) {
            sendQuietly(customerId, session, payload);
        }
    }

    @Override
    public void broadcastAll(WsEnvelope envelope) {
        String payload = envelope.toJson();
        Set<WebSocketSession> distinct = new CopyOnWriteArraySet<>();
        staffSessions.values().forEach(distinct::addAll);
        customerSessions.values().forEach(distinct::addAll);
        distinct.forEach(session -> sendQuietly(0L, session, payload));
    }

    @Override
    public int closeIdle(long idleTimeoutMillis) {
        long threshold = now() - idleTimeoutMillis;
        List<WebSocketSession> stale = new ArrayList<>();
        lastActive.forEach((session, last) -> {
            if (last <= threshold) {
                stale.add(session);
            }
        });
        int closed = 0;
        for (WebSocketSession session : stale) {
            lastActive.remove(session);
            staffSessions.forEach((staffId, set) -> set.remove(session));
            staffSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            customerSessions.forEach((customerId, set) -> set.remove(session));
            customerSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                }
                closed++;
            } catch (Exception e) {
                log.warn("关闭空闲 WebSocket 会话失败, sessionId={}", session.getId(), e);
            }
        }
        return closed;
    }

    @Override
    public int onlineCount() {
        return staffSessions.values().stream().mapToInt(Set::size).sum()
                + customerSessions.values().stream().mapToInt(Set::size).sum();
    }

    private void sendQuietly(long staffId, WebSocketSession session, String payload) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                log.warn("WebSocket 广播失败, staffId={}, sessionId={}", staffId, session.getId(), e);
            }
        }
    }

    private long now() {
        return clock.instant().toEpochMilli();
    }
}
