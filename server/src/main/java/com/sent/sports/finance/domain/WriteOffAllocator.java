package com.sent.sports.finance.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 回款核销分配（plan §7.7 / spec §6.8.4，验收 A9/A18）：
 * <pre>
 *   一笔记多单；Σ 分配额 必须 ≤ 付款单可核销余额（否则 WRITE_OFF_OVERFLOW 40930）
 *   单笔：分配额 必须 ≤ 应收 remaining（否则 WRITE_OFF_OVERFLOW 40930）
 *   不允许预收款：分配总额必须精确，多余金额无去处，直接拒绝
 *   应收收满 → CLOSED
 * </pre>
 * <p>所有拒绝都带「本次金额 / 上限 / 差额」的具体数字：操作员在核销工作台只能看到
 * 前端原样弹出的 message，泛化的「核销金额超额」无法定位到底是回款单不够还是某张应收收满了。</p>
 */
public final class WriteOffAllocator {

    private WriteOffAllocator() {
    }

    /** 付款单快照：总额与已核销额（分）。 */
    public record PaymentRecord(long amountCent, long writtenOffCent) {
    }

    /** 应收快照：总额与已收额（分）。 */
    public record ReceivableSnapshot(long amountCent, long receivedCent) {
    }

    /** 一条核销分配：应收 id、本次分配额、应收快照。 */
    public record WriteOffItem(long receivableId, long assignAmount, ReceivableSnapshot receivable) {
    }

    /** 单笔核销结果。 */
    public record AllocatedReceivable(long receivableId, long newReceivedCent, boolean closed) {
    }

    /** 整体核销结果。 */
    public record WriteOffResult(long totalWrittenOff, List<AllocatedReceivable> items) {
    }

    public static WriteOffResult allocate(PaymentRecord payment, List<WriteOffItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.BIZ_REJECT, "请至少勾选一笔应收并填写本次核销金额");
        }
        long total = 0;
        for (WriteOffItem it : items) {
            total += it.assignAmount();
        }
        if (total <= 0) {
            throw new BizException(ErrorCode.BIZ_REJECT, "本次核销合计金额必须大于 0 元");
        }
        long available = payment.amountCent() - payment.writtenOffCent();
        if (total > available) {
            throw new BizException(ErrorCode.WRITE_OFF_OVERFLOW, String.format(
                    "本次核销合计 %s 元，超过该回款单最大可核销金额 %s 元"
                            + "（回款金额 %s 元，已核销 %s 元）；请下调核销金额 %s 元",
                    MoneyUtil.toYuan(total), MoneyUtil.toYuan(available),
                    MoneyUtil.toYuan(payment.amountCent()), MoneyUtil.toYuan(payment.writtenOffCent()),
                    MoneyUtil.toYuan(total - available)));
        }

        List<AllocatedReceivable> result = new ArrayList<>();
        for (WriteOffItem it : items) {
            if (it.assignAmount() <= 0) {
                throw new BizException(ErrorCode.BIZ_REJECT, String.format(
                        "应收单 %d 的本次核销金额必须大于 0 元（当前未填写或为 0），"
                                + "不需要核销的应收请取消勾选", it.receivableId()));
            }
            ReceivableSnapshot r = it.receivable();
            long remaining = r.amountCent() - r.receivedCent();
            if (it.assignAmount() > remaining) {
                throw new BizException(ErrorCode.WRITE_OFF_OVERFLOW, String.format(
                        "应收单 %d 本次核销 %s 元，超过该应收单剩余可核销金额 %s 元"
                                + "（应收 %s 元，已核销 %s 元）",
                        it.receivableId(), MoneyUtil.toYuan(it.assignAmount()),
                        MoneyUtil.toYuan(remaining), MoneyUtil.toYuan(r.amountCent()),
                        MoneyUtil.toYuan(r.receivedCent())));
            }
            long newReceived = r.receivedCent() + it.assignAmount();
            boolean closed = newReceived >= r.amountCent();
            result.add(new AllocatedReceivable(it.receivableId(), newReceived, closed));
        }
        return new WriteOffResult(total, result);
    }
}
