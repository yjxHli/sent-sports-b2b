package com.sent.sports.finance.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-39 应收状态测试（TDD 红）：plan §7.7 / spec §6.8。
 * 到期日、逾期判定、结清。
 */
class ReceivableTest {

    private final LocalDate TODAY = LocalDate.of(2024, 6, 15);

    @Test
    void futureDueNotOverdue() {
        Receivable r = new Receivable(10000, 0, LocalDate.of(2024, 7, 1), ReceivableStatus.NORMAL);
        assertFalse(r.isOverdue(TODAY));
        r.refreshStatus(TODAY);
        assertEquals(ReceivableStatus.NORMAL, r.getStatus());
    }

    @Test
    void pastDueUnpaidIsOverdue() {
        Receivable r = new Receivable(10000, 0, LocalDate.of(2024, 6, 1), ReceivableStatus.NORMAL);
        assertTrue(r.isOverdue(TODAY));
        r.refreshStatus(TODAY);
        assertEquals(ReceivableStatus.OVERDUE, r.getStatus());
    }

    @Test
    void fullyReceivedIsClearedNotOverdue() {
        Receivable r = new Receivable(10000, 10000, LocalDate.of(2024, 1, 1), ReceivableStatus.NORMAL);
        assertTrue(r.isCleared());
        assertFalse(r.isOverdue(TODAY));
        r.refreshStatus(TODAY);
        assertEquals(ReceivableStatus.CLOSED, r.getStatus());
    }

    @Test
    void receiveClosesWhenFull() {
        Receivable r = new Receivable(10000, 0, LocalDate.of(2024, 1, 1), ReceivableStatus.NORMAL);
        r.receive(10000);
        assertTrue(r.isCleared());
        assertEquals(ReceivableStatus.CLOSED, r.getStatus());
    }

    @Test
    void remainingCent() {
        Receivable r = new Receivable(10000, 3000, LocalDate.of(2024, 7, 1), ReceivableStatus.NORMAL);
        assertEquals(7000L, r.remainingCent());
    }
}
