package com.sent.sports.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.auth.service.SendSmsCodeUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发送验证码用例测试（TDD 红）：生成 6 位码、控制台日志可直读、限流与手机号校验。
 */
class SendSmsCodeUseCaseTest extends BaseUnitTest {

    private static final String PHONE = "13800138000";

    @Mock
    private SmsCodeStore smsCodeStore;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private SendSmsCodeUseCase useCase;
    @Captor
    private ArgumentCaptor<String> codeCaptor;

    @Test
    void generatesSixDigitCodeStoredWith60sTtl() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);

        String code = useCase.send(PHONE);

        verify(smsCodeStore).save(eq(SmsCodeStore.SCOPE_LOGIN), eq(PHONE), codeCaptor.capture(),
                eq(FIXED_INSTANT.toEpochMilli() + 60_000L));
        assertTrue(code.matches("\\d{6}"), "验证码应为 6 位数字");
        assertEquals(code, codeCaptor.getValue(), "返回值应与存入的验证码一致（供演示回显）");
    }

    @Test
    void printsCodeToBackendConsole() {
        Logger logger = (Logger) LoggerFactory.getLogger(SendSmsCodeUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(clock.instant()).thenReturn(FIXED_INSTANT);

            useCase.send(PHONE);

            verify(smsCodeStore).save(eq(SmsCodeStore.SCOPE_LOGIN), eq(PHONE), codeCaptor.capture(), anyLong());
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining(" "));
            assertTrue(logs.contains(codeCaptor.getValue()), "验证码必须打印到后端控制台日志");
            assertTrue(logs.contains("138****8000"), "日志中手机号必须脱敏");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void rejectsResendWithinWindow() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.hasValid(SmsCodeStore.SCOPE_LOGIN, PHONE, FIXED_INSTANT.toEpochMilli())).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> useCase.send(PHONE));

        assertEquals(ErrorCode.TOO_FREQUENT, e.getErrorCode());
        verify(smsCodeStore, never()).save(any(), any(), any(), anyLong());
    }

    @Test
    void rejectsInvalidPhone() {
        BizException e = assertThrows(BizException.class, () -> useCase.send("12345"));

        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }

    @Test
    void resetCodeUsesDedicatedScopeAndIsPrintedToConsole() {
        Logger logger = (Logger) LoggerFactory.getLogger(SendSmsCodeUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(clock.instant()).thenReturn(FIXED_INSTANT);

            String code = useCase.sendForReset(PHONE);

            verify(smsCodeStore).save(eq(SmsCodeStore.SCOPE_RESET), eq(PHONE), eq(code),
                    eq(FIXED_INSTANT.toEpochMilli() + 60_000L));
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining(" "));
            assertTrue(logs.contains(code), "找回密码验证码也要打印到后端控制台");
            assertTrue(logs.contains("找回密码"), "日志要区分用途");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
