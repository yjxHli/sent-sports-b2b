package com.sent.sports.finance;

import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoiceNotifyRow;
import com.sent.sports.finance.service.impl.InvoiceNotificationServiceImpl;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 发票站内信：成功 / 失败异常 / 驳回 / 用户端申请被拒四类都要送到下单客户。 */
@ExtendWith(MockitoExtension.class)
class InvoiceNotificationServiceTest {

    @Mock
    private InvoiceMapper invoiceMapper;
    @Mock
    private MessageSendService messageSendService;
    @InjectMocks
    private InvoiceNotificationServiceImpl service;

    private static InvoiceNotifyRow row() {
        InvoiceNotifyRow row = new InvoiceNotifyRow();
        row.setInvoiceId(31L);
        row.setCustomerId(9002L);
        row.setOrderNo("SO-INV-1");
        row.setInvoiceNo("INV-31");
        row.setStatus("ISSUED");
        row.setAmountCent(150000L);
        return row;
    }

    @Test
    @DisplayName("开票成功：发票号 / 订单号 / 金额进正文，bizNo 指回订单")
    void notifiesIssued() {
        when(invoiceMapper.selectNotifyRow(31L)).thenReturn(row());

        service.issued(31L);

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        InboxMessage msg = captor.getValue();
        assertThat(msg.customerId()).isEqualTo(9002L);
        assertThat(msg.msgType()).isEqualTo("ORDER");
        assertThat(msg.title()).isEqualTo("发票开具成功");
        assertThat(msg.content()).contains("INV-31", "SO-INV-1", "1,500.00");
        assertThat(msg.bizType()).isEqualTo("ORDER");
        assertThat(msg.bizNo()).isEqualTo("SO-INV-1");
    }

    @Test
    @DisplayName("标记异常：异常原因必须带进正文，让客户知道要补什么")
    void notifiesExceptionWithReason() {
        when(invoiceMapper.selectNotifyRow(31L)).thenReturn(row());

        service.exception(31L, "抬头与税号不匹配");

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("发票开具异常");
        assertThat(captor.getValue().content()).contains("抬头与税号不匹配", "INV-31");
    }

    @Test
    @DisplayName("驳回：驳回原因必须带进正文")
    void notifiesRejectedWithReason() {
        when(invoiceMapper.selectNotifyRow(31L)).thenReturn(row());

        service.rejected(31L, "发票抬头有误");

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("开票申请被驳回");
        assertThat(captor.getValue().content()).contains("发票抬头有误", "SO-INV-1");
    }

    @Test
    @DisplayName("用户端申请被拒：没有发票行也照样通知到客户")
    void notifiesUserApplicationRejected() {
        service.applicationRejected(9002L, "SO-INV-1", "订单完成后才能申请开票");

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("开票申请被拒");
        assertThat(captor.getValue().content()).contains("SO-INV-1", "订单完成后才能申请开票");
        assertThat(captor.getValue().customerId()).isEqualTo(9002L);
    }

    @Test
    @DisplayName("发票行不存在时不发信，历史脏数据不能把动作打回 500")
    void skipsMissingInvoice() {
        when(invoiceMapper.selectNotifyRow(999L)).thenReturn(null);

        service.issued(999L);

        verify(messageSendService, never()).send(any());
    }
}
