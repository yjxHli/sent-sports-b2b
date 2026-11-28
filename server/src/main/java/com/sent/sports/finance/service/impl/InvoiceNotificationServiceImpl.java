package com.sent.sports.finance.service.impl;

import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoiceNotifyRow;
import com.sent.sports.finance.service.InvoiceNotificationService;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 开票站内信实现：发票行不存在 / 客户 ID 无效时静默跳过，
 * 历史脏数据不能把「开票成功」这种已提交的动作打回失败。
 */
@Service
public class InvoiceNotificationServiceImpl implements InvoiceNotificationService {

    private final InvoiceMapper invoiceMapper;
    private final MessageSendService messageSendService;

    public InvoiceNotificationServiceImpl(InvoiceMapper invoiceMapper,
                                          MessageSendService messageSendService) {
        this.invoiceMapper = invoiceMapper;
        this.messageSendService = messageSendService;
    }

    @Override
    public void issued(long invoiceId) {
        InvoiceNotifyRow row = invoiceMapper.selectNotifyRow(invoiceId);
        if (row == null) {
            return;
        }
        String content = "订单 " + nz(row.getOrderNo()) + " 的发票 " + nz(row.getInvoiceNo())
                + " 已开具，金额 ¥" + money(row.getAmountCent())
                + "。可在「我的订单」中查看或下载。";
        send(row, "发票开具成功", content);
    }

    @Override
    public void exception(long invoiceId, String reason) {
        InvoiceNotifyRow row = invoiceMapper.selectNotifyRow(invoiceId);
        if (row == null) {
            return;
        }
        String content = "订单 " + nz(row.getOrderNo()) + " 的发票 " + nz(row.getInvoiceNo())
                + " 开具异常。原因：" + clean(reason)
                + "。财务正在处理，请留意后续通知。";
        send(row, "发票开具异常", content);
    }

    @Override
    public void rejected(long invoiceId, String reason) {
        InvoiceNotifyRow row = invoiceMapper.selectNotifyRow(invoiceId);
        if (row == null) {
            return;
        }
        String content = "订单 " + nz(row.getOrderNo()) + " 的开票申请 " + nz(row.getInvoiceNo())
                + " 未通过。原因：" + clean(reason)
                + "。请修正发票信息后重新提交。";
        send(row, "开票申请被驳回", content);
    }

    /**
     * 申请被拒后主流程会抛 40901，{@link com.sent.sports.common.anno.UseCase} 事务随即回滚。
     * 因此这封信必须 REQUIRES_NEW 独立提交，否则用户永远收不到「为什么被拒」。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applicationRejected(long customerId, String orderNo, String reason) {
        if (customerId <= 0L) {
            return;
        }
        String content = "订单 " + nz(orderNo) + " 的开票申请未通过。原因：" + clean(reason)
                + "。如有疑问请联系客服。";
        messageSendService.send(new InboxMessage(customerId, "ORDER", "开票申请被拒",
                content, "ORDER", nz(orderNo)));
    }

    private void send(InvoiceNotifyRow row, String title, String content) {
        if (row.getCustomerId() == null || row.getCustomerId() <= 0L) {
            return;
        }
        messageSendService.send(new InboxMessage(row.getCustomerId(), "ORDER", title,
                content, "ORDER", nz(row.getOrderNo())));
    }

    private static String money(Long cent) {
        return String.format(Locale.CHINA, "%,.2f", (cent == null ? 0L : cent) / 100.0);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String clean(String reason) {
        String value = reason == null ? "" : reason.trim();
        return value.isEmpty() ? "未填写具体原因" : value;
    }
}
