package com.sent.sports.notify;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 邮件门面单测（P5-37）：开发实现不外发、空收件人拒绝。 */
class MailSenderTest {

    private final MailSender sender = new MailSender.LoggingMailSender();

    @Test
    @DisplayName("正常参数不抛异常（只记日志）")
    void sendsWithoutThrowing() {
        assertThatCode(() -> sender.send("ops@sent.test", "应收逾期提醒", "3 笔应收已逾期")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空收件人抛 PARAM_INVALID，避免通知被静默丢弃")
    void blankRecipientIsRejected() {
        assertThatThrownBy(() -> sender.send("  ", "主题", "正文"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }
}
