package com.sent.sports.notify;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 邮件发送门面（P5-37）：本期只做「打到日志」的桩实现，避免开发环境误发真实邮件。
 * <p>为什么抽接口：真实实现（SMTP）需要账号、限流、模板与重试策略，属于部署期配置；
 * 业务侧只关心「发一封通知」，接口固定后替换实现不影响调用方。
 * <p>生产接入点：新增 {@code SmtpMailSender}，按 {@code app.env=prod} 条件装配即可，本类保持为开发默认。
 */
public interface MailSender {

    /** 发送纯文本邮件；收件人为空直接拒绝（宁可报错，也不要静默丢弃通知）。 */
    void send(String to, String subject, String body);

    /** 开发/测试默认实现：只记日志，不外发。 */
    @Service
    class LoggingMailSender implements MailSender {

        private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

        @Override
        public void send(String to, String subject, String body) {
            if (to == null || to.isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "收件人不能为空");
            }
            // 脱敏：邮件正文可能含订单 / 账户等敏感信息，开发桩只记录收件人与主题，正文一律遮蔽。
            log.info("[MAIL-DEV] to={} subject={} body=[REDACTED,len={}]", to, subject,
                    body == null ? 0 : body.length());
        }
    }
}
