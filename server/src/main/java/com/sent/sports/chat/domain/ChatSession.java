package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.List;

/**
 * 客服会话实体（plan §7.8 / spec §6.10）：待接入 / 进行中 / 已结束。
 * 同一客户仅允许一个 ACTIVE 会话（业务约束由 {@link #hasActiveSession}/{@link #canStartNew} 校验）。
 * 纯 POJO，不依赖 Spring。
 */
public class ChatSession {

    private final long id;
    private final long customerId;
    private final boolean vip;
    private ChatSessionStatus status;
    private Long staffId;
    private String closeReason;
    private int seq;
    private long createdAtMillis;
    private long lastActiveAtMillis;

    public ChatSession(long id, long customerId) {
        this(id, customerId, false, ChatSessionStatus.WAITING, null, 0L, 0L, 0);
    }

    public ChatSession(long id, long customerId, ChatSessionStatus status, Long staffId) {
        this(id, customerId, false, status, staffId, 0L, 0L, 0);
    }

    public ChatSession(long id, long customerId, boolean vip, ChatSessionStatus status, Long staffId,
                       long createdAtMillis, long lastActiveAtMillis, int seq) {
        this.id = id;
        this.customerId = customerId;
        this.vip = vip;
        this.status = status;
        this.staffId = staffId;
        this.createdAtMillis = createdAtMillis;
        this.lastActiveAtMillis = lastActiveAtMillis;
        this.seq = seq;
    }

    /** 分配客服：WAITING → ACTIVE；非 WAITING 抛 STATE_CONFLICT(40901)。 */
    public void assign(long staffId) {
        if (status != ChatSessionStatus.WAITING) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        this.staffId = staffId;
        this.status = ChatSessionStatus.ACTIVE;
    }

    /** 结束会话：ACTIVE/WAITING → CLOSED；重复关闭抛 STATE_CONFLICT(40901)。 */
    public void close(String reason) {
        if (status == ChatSessionStatus.CLOSED) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        this.status = ChatSessionStatus.CLOSED;
        this.closeReason = reason;
    }

    /**
     * 从持久化状态还原（含结束原因），<b>不触发状态机校验</b>。
     * <p>读库时不能用 {@link #close(String)} 落「已结束」状态——那会把「加载」当成「再关一次」，
     * 直接抛 STATE_CONFLICT，导致任何包含已结束会话的查询（我的会话列表、建会话复用判断）整体失败。</p>
     */
    public static ChatSession rehydrate(long id, long customerId, boolean vip, ChatSessionStatus status,
                                        Long staffId, String closeReason,
                                        long createdAtMillis, long lastActiveAtMillis, int seq) {
        ChatSession session = new ChatSession(id, customerId, vip, status, staffId,
                createdAtMillis, lastActiveAtMillis, seq);
        session.closeReason = closeReason;
        return session;
    }

    /**
     * 转接：保持 ACTIVE 仅换接待客服；非 ACTIVE 抛 STATE_CONFLICT(40901)。
     * 历史消息保留（不删除），由查询层保证接收方可见。
     */
    public void reassign(long staffId) {
        if (status != ChatSessionStatus.ACTIVE) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        this.staffId = staffId;
    }

    /** 分配下一条消息序号；已结束会话拒绝(STATE_CONFLICT)。 */
    public int appendMessage() {
        if (status == ChatSessionStatus.CLOSED) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        return ++seq;
    }

    /** 释放回排队：ACTIVE → WAITING，清空接待客服（释放名额）。 */
    public void release() {
        this.staffId = null;
        this.status = ChatSessionStatus.WAITING;
    }

    /** 是否已超过 inactive 超时阈值（仅未结束会话参与计算）。 */
    public boolean isExpired(long nowMillis, long timeoutMillis) {
        return status != ChatSessionStatus.CLOSED
                && (lastActiveAtMillis + timeoutMillis) <= nowMillis;
    }

    /** 刷新最近活跃时间（发送消息 / 创建会话时调用）。 */
    public void touch(long nowMillis) {
        this.lastActiveAtMillis = nowMillis;
    }

    public boolean isActive() {
        return status == ChatSessionStatus.ACTIVE;
    }

    public boolean isVip() {
        return vip;
    }

    /** 同一客户是否存在进行中会话。 */
    public static boolean hasActiveSession(List<ChatSession> sessions, long customerId) {
        return sessions.stream()
                .anyMatch(s -> s.customerId == customerId && s.status == ChatSessionStatus.ACTIVE);
    }

    /**
     * 取该客户「未结束」的会话（ACTIVE 或 WAITING）。
     * <p>用户重复进入客服页必须复用同一会话：只复用 ACTIVE 会让同一位客户堆积多个 WAITING 会话，
     * 客服工作台就会出现多条同一客户的待接入记录（接待时也无法确定接哪一个）。</p>
     */
    public static java.util.Optional<ChatSession> findOpen(List<ChatSession> sessions, long customerId) {
        return sessions.stream()
                .filter(s -> s.customerId == customerId && s.status != ChatSessionStatus.CLOSED)
                .findFirst();
    }

    /** 同一客户是否可发起新会话（无进行中会话时为真）。 */
    public static boolean canStartNew(List<ChatSession> sessions, long customerId) {
        return !hasActiveSession(sessions, customerId);
    }

    public long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public ChatSessionStatus getStatus() {
        return status;
    }

    public Long getStaffId() {
        return staffId;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getLastActiveAtMillis() {
        return lastActiveAtMillis;
    }

    /** 当前消息序号水位（持久化与断线补拉的依据）。 */
    public int getSeq() {
        return seq;
    }
}
