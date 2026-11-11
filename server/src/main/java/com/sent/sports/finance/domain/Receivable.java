package com.sent.sports.finance.domain;

import java.time.LocalDate;

/**
 * 应收实体（plan §7.7 / spec §6.8）：到期日、逾期判定、结清。
 * 逾期由定时任务或 {@link #refreshStatus(LocalDate)} 重算；结清即 CLOSED。
 */
public class Receivable {

    private final long id;
    private final long amountCent;
    private long receivedCent;
    private final LocalDate dueDate;
    private ReceivableStatus status;

    public Receivable(long amountCent, long receivedCent, LocalDate dueDate, ReceivableStatus status) {
        this(0L, amountCent, receivedCent, dueDate, status);
    }

    /** 带主键的构造（从仓储加载时使用；主键 0 表示尚未落库）。 */
    public Receivable(long id, long amountCent, long receivedCent, LocalDate dueDate, ReceivableStatus status) {
        this.id = id;
        this.amountCent = amountCent;
        this.receivedCent = receivedCent;
        this.dueDate = dueDate;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    /** 是否逾期：未结清 且 已过期 且 未收满。 */
    public boolean isOverdue(LocalDate today) {
        return status != ReceivableStatus.CLOSED
                && dueDate.isBefore(today)
                && receivedCent < amountCent;
    }

    /** 按当前日期重算状态：收满→CLOSED；过期未收满→OVERDUE；否则 NORMAL。 */
    public void refreshStatus(LocalDate today) {
        if (receivedCent >= amountCent) {
            status = ReceivableStatus.CLOSED;
        } else if (dueDate.isBefore(today)) {
            status = ReceivableStatus.OVERDUE;
        } else {
            status = ReceivableStatus.NORMAL;
        }
    }

    /** 回款（分）：累计已收，收满自动 CLOSED。 */
    public void receive(long amount) {
        if (amount <= 0) {
            return;
        }
        receivedCent += amount;
        if (receivedCent >= amountCent) {
            status = ReceivableStatus.CLOSED;
        }
    }

    /** 回退已收金额（冲销）：减少已收额，不低于 0。 */
    public void unreceive(long amount) {
        if (amount <= 0) {
            return;
        }
        receivedCent = Math.max(0, receivedCent - amount);
    }

    public boolean isCleared() {
        return receivedCent >= amountCent;
    }

    public long remainingCent() {
        return Math.max(0, amountCent - receivedCent);
    }

    public long getAmountCent() {
        return amountCent;
    }

    public long getReceivedCent() {
        return receivedCent;
    }

    public ReceivableStatus getStatus() {
        return status;
    }
}
