package com.sent.sports.finance.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-37 核销分配测试（TDD 红）：plan §7.7 / spec §6.8.4（验收 A9/A18）。
 * 一笔记多单、单笔超额抛 WRITE_OFF_OVERFLOW(40930)、付款单总额超额抛 40930、
 * 分次回款、顺序核销、结清标记。不允许预收款（Σ 必须等于本次核销额）。
 */
class WriteOffAllocatorTest {

    private WriteOffAllocator.ReceivableSnapshot recv(long amount, long received) {
        return new WriteOffAllocator.ReceivableSnapshot(amount, received);
    }

    private WriteOffAllocator.WriteOffItem item(long id, long assign, long amount, long received) {
        return new WriteOffAllocator.WriteOffItem(id, assign, recv(amount, received));
    }

    @Test
    void singleReceivableFullyPaidClosed() {
        WriteOffAllocator.PaymentRecord pay = new WriteOffAllocator.PaymentRecord(10000, 0);
        WriteOffAllocator.WriteOffResult r = WriteOffAllocator.allocate(pay, List.of(item(1, 10000, 10000, 0)));
        assertTrue(r.items().get(0).closed());
        assertEquals(10000L, r.totalWrittenOff());
    }

    @Test
    void multipleReceivablesOnePayment() {
        WriteOffAllocator.PaymentRecord pay = new WriteOffAllocator.PaymentRecord(20000, 0);
        WriteOffAllocator.WriteOffResult r = WriteOffAllocator.allocate(pay, List.of(
                item(1, 6000, 10000, 0),
                item(2, 4000, 10000, 0)));
        assertFalse(r.items().get(0).closed());
        assertFalse(r.items().get(1).closed());
        assertEquals(10000L, r.totalWrittenOff());
    }

    @Test
    void overflowOnReceivableThrows() {
        WriteOffAllocator.PaymentRecord pay = new WriteOffAllocator.PaymentRecord(20000, 0);
        BizException ex = assertThrows(BizException.class,
                () -> WriteOffAllocator.allocate(pay, List.of(item(1, 11000, 10000, 0))));
        assertEquals(ErrorCode.WRITE_OFF_OVERFLOW, ex.getErrorCode());
    }

    @Test
    void overflowOnPaymentThrows() {
        WriteOffAllocator.PaymentRecord pay = new WriteOffAllocator.PaymentRecord(5000, 0);
        BizException ex = assertThrows(BizException.class,
                () -> WriteOffAllocator.allocate(pay, List.of(item(1, 6000, 10000, 0))));
        assertEquals(ErrorCode.WRITE_OFF_OVERFLOW, ex.getErrorCode());
    }

    @Test
    void partialThenCompleteAcrossTimes() {
        // 第一次核销 4000
        WriteOffAllocator.PaymentRecord p1 = new WriteOffAllocator.PaymentRecord(10000, 0);
        WriteOffAllocator.WriteOffResult r1 = WriteOffAllocator.allocate(p1, List.of(item(1, 4000, 10000, 0)));
        assertFalse(r1.items().get(0).closed());

        // 第二次核销剩余 6000（付款单已核销 4000）
        WriteOffAllocator.PaymentRecord p2 = new WriteOffAllocator.PaymentRecord(10000, 4000);
        WriteOffAllocator.WriteOffResult r2 = WriteOffAllocator.allocate(p2, List.of(item(1, 6000, 10000, 4000)));
        assertTrue(r2.items().get(0).closed());
        assertEquals(6000L, r2.totalWrittenOff());
    }

    @Test
    @DisplayName("回款单超额：提示本次核销合计、最大可核销金额及回款/已核销构成")
    void overflowOnPaymentExplainsAmounts() {
        // 回款 50.00 已核销 12.00 → 最多还能核销 38.00；本次填 60.00
        WriteOffAllocator.PaymentRecord pay = new WriteOffAllocator.PaymentRecord(5000, 1200);
        BizException ex = assertThrows(BizException.class,
                () -> WriteOffAllocator.allocate(pay, List.of(item(1, 6000, 10000, 0))));

        assertEquals(ErrorCode.WRITE_OFF_OVERFLOW, ex.getErrorCode());
        String msg = ex.resolvedMessage();
        assertTrue(msg.contains("60.00"), "应提示本次核销金额，实际：" + msg);
        assertTrue(msg.contains("38.00"), "应提示最大可核销金额 38.00，实际：" + msg);
        assertTrue(msg.contains("可核销"), "应出现「可核销」字样，实际：" + msg);
    }

    @Test
    @DisplayName("单笔应收超额：提示该应收单号、剩余可核销金额与本次填写金额")
    void overflowOnReceivableExplainsRemaining() {
        // 应收 100.00 已收 20.00 → 剩 80.00；本次填 110.00（未超回款单 200.00）
        WriteOffAllocator.PaymentRecord pay = new WriteOffAllocator.PaymentRecord(20000, 0);
        BizException ex = assertThrows(BizException.class,
                () -> WriteOffAllocator.allocate(pay, List.of(item(444, 11000, 10000, 2000))));

        assertEquals(ErrorCode.WRITE_OFF_OVERFLOW, ex.getErrorCode());
        String msg = ex.resolvedMessage();
        assertTrue(msg.contains("444"), "应提示应收单号，实际：" + msg);
        assertTrue(msg.contains("110.00"), "应提示本次填写金额 110.00，实际：" + msg);
        assertTrue(msg.contains("80.00"), "应提示该应收剩余 80.00，实际：" + msg);
    }

    @Test
    @DisplayName("核销金额为 0 或负数：提示必须大于 0，而不是泛化的「业务校验失败」")
    void nonPositiveAmountExplainsRule() {
        WriteOffAllocator.PaymentRecord pay = new WriteOffAllocator.PaymentRecord(10000, 0);

        BizException total = assertThrows(BizException.class,
                () -> WriteOffAllocator.allocate(pay, List.of(item(1, 0, 10000, 0))));
        assertTrue(total.resolvedMessage().contains("大于 0"), total.resolvedMessage());

        BizException line = assertThrows(BizException.class,
                () -> WriteOffAllocator.allocate(pay, List.of(item(1, 5000, 10000, 0), item(2, -1, 10000, 0))));
        assertTrue(line.resolvedMessage().contains("大于 0"), line.resolvedMessage());
    }
}
