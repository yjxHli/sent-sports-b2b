package com.sent.sports.refund;

import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.service.RefundSuccessNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundSuccessNotifierTest {

    @Mock
    private OrderRefundViewService orderRefundViewService;
    @Mock
    private MessageSendService messageSendService;

    private RefundSuccessNotifier notifier() {
        return new RefundSuccessNotifier(orderRefundViewService, messageSendService);
    }

    private RefundOrder refund() {
        return new RefundOrder("RF-1", "SO-1", 11L, 5000L, "REFUND", "reason",
                RefundStatus.REFUNDED, 0L);
    }

    @Test
    @DisplayName("退款成功：给下单客户发 ORDER 站内信，带退款单号与金额")
    void notifiesCustomerOnSuccess() {
        when(orderRefundViewService.getView("SO-1")).thenReturn(Optional.of(
                new OrderRefundView("SO-1", 9002L, 11L, List.of(), 5000L, false)));

        notifier().notify(refund());

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        InboxMessage message = captor.getValue();
        assertThat(message.customerId()).isEqualTo(9002L);
        assertThat(message.msgType()).isEqualTo("ORDER");
        assertThat(message.title()).isEqualTo("退款成功");
        assertThat(message.content()).contains("RF-1", "SO-1", "50.00");
        assertThat(message.bizType()).isEqualTo("REFUND");
        assertThat(message.bizNo()).isEqualTo("RF-1");
    }

    @Test
    @DisplayName("订单视图缺失时不发信，不能让退款成功动作失败")
    void skipsWhenOrderViewMissing() {
        when(orderRefundViewService.getView("SO-1")).thenReturn(Optional.empty());

        notifier().notify(refund());

        verify(messageSendService, never()).send(org.mockito.ArgumentMatchers.any());
    }
}
