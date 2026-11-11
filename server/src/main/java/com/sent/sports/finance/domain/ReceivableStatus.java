package com.sent.sports.finance.domain;

/**
 * 应收状态（plan §7.7）：正常 / 逾期 / 结清。
 */
public enum ReceivableStatus {
    NORMAL,
    OVERDUE,
    CLOSED
}
