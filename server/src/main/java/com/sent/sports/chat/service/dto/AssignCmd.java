package com.sent.sports.chat.service.dto;

/**
 * 分配命令（中立入参）：目标会话、接待客服、操作人。
 * <p>{@code sessionId > 0} 时锁定该会话接待（客服工作台点「接待」走的路径，语义明确）；
 * {@code sessionId <= 0} 时退化为「从排队队列拉取一个」（VIP 优先、再按最早创建）的调度语义。</p>
 */
public record AssignCmd(long sessionId, long staffId, long operatorId) {

    /** 兼容旧调用：只给客服与操作人 → 按排队队列拉取。 */
    public AssignCmd(long staffId, long operatorId) {
        this(0L, staffId, operatorId);
    }
}
