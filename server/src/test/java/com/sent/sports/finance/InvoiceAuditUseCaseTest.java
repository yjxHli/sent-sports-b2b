package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoiceNotifyRow;
import com.sent.sports.finance.service.InvoiceAuditUseCase;
import com.sent.sports.finance.service.InvoiceNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 管理端新增的「驳回 / 标记异常」动作：状态写入 + 站内信缺一不可。 */
@ExtendWith(MockitoExtension.class)
class InvoiceAuditUseCaseTest {

    @Mock
    private InvoiceMapper invoiceMapper;
    @Mock
    private InvoiceNotificationService notificationService;
    @InjectMocks
    private InvoiceAuditUseCase useCase;

    private static InvoiceNotifyRow row(String status) {
        InvoiceNotifyRow row = new InvoiceNotifyRow();
        row.setInvoiceId(31L);
        row.setCustomerId(9002L);
        row.setOrderNo("SO-INV-1");
        row.setInvoiceNo("INV-31");
        row.setStatus(status);
        return row;
    }

    @Test
    @DisplayName("驳回：APPLIED → REJECTED 并通知，原因不能为空")
    void rejectsWithReason() {
        when(invoiceMapper.selectNotifyRow(31L)).thenReturn(row("APPLIED"));
        when(invoiceMapper.markRejected(31L)).thenReturn(1);

        String status = useCase.markRejected(31L, "  抬头有误  ");

        assertThat(status).isEqualTo("REJECTED");
        verify(notificationService).rejected(31L, "抬头有误");
    }

    @Test
    @DisplayName("标记异常：ISSUED → EXCEPTION 并通知（开票失败/异常的运营处置）")
    void marksException() {
        when(invoiceMapper.selectNotifyRow(31L)).thenReturn(row("ISSUED"));
        when(invoiceMapper.markException(31L)).thenReturn(1);

        String status = useCase.markException(31L, "税号校验失败");

        assertThat(status).isEqualTo("EXCEPTION");
        verify(notificationService).exception(31L, "税号校验失败");
    }

    @Test
    @DisplayName("驳回必须填原因，且不写库不发信")
    void blankReasonRejected() {
        assertThatThrownBy(() -> useCase.markRejected(31L, "  "))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        verify(invoiceMapper, never()).markRejected(31L);
        verify(notificationService, never()).rejected(eq(31L), anyString());
    }

    @Test
    @DisplayName("单据不存在 / 已被处理（影响 0 行）都不发通知")
    void noNotificationWithoutStateChange() {
        when(invoiceMapper.selectNotifyRow(31L)).thenReturn(row("APPLIED"));
        when(invoiceMapper.markRejected(31L)).thenReturn(0);

        assertThatThrownBy(() -> useCase.markRejected(31L, "抬头有误"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT));
        verify(notificationService, never()).rejected(eq(31L), anyString());

        when(invoiceMapper.selectNotifyRow(999L)).thenReturn(null);
        assertThatThrownBy(() -> useCase.markException(999L, "异常"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(notificationService, never()).exception(eq(999L), anyString());
    }
}
