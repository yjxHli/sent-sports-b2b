package com.sent.sports.auth;

import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.auth.service.EnterpriseAuditProgressUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.EnterpriseProfileQueryService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业审核进度查询（TDD 红 → 绿）：未审核通过的企业拿不到令牌，
 * 因此进度页必须「手机号 + 短信验证码」匿名查询，且验证码一次性消费，
 * 不能让任何人输入手机号就看到别家企业的营业执照与驳回原因。
 */
class EnterpriseAuditProgressUseCaseTest extends BaseUseCaseTest {

    private static final String PHONE = "13912345678";
    private static final String CODE = "123456";
    private static final long NOW = 1_793_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Mock
    private SmsCodeStore smsCodeStore;

    @Mock
    private EnterpriseProfileQueryService profileQueryService;

    private EnterpriseAuditProgressUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EnterpriseAuditProgressUseCase(smsCodeStore, profileQueryService, CLOCK);
    }

    @Test
    void pendingProgressVisibleAfterSmsCodeVerified() {
        EnterpriseProfileQueryService.AuditProgress pending =
                new EnterpriseProfileQueryService.AuditProgress(9100L, PHONE, "森特测试有限公司",
                        "91310000MA1FL0AB1X", "FILE-1", "PENDING", null, null, NOW - 86_400_000L);
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(true);
        when(profileQueryService.findByPhone(PHONE)).thenReturn(pending);

        EnterpriseProfileQueryService.AuditProgress result = useCase.get(PHONE, CODE);

        assertThat(result).isEqualTo(pending);
        assertThat(result.status()).isEqualTo("PENDING");
        verify(smsCodeStore).verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW);
    }

    @Test
    void rejectedProgressExposesReasonAndSubmittedMaterials() {
        EnterpriseProfileQueryService.AuditProgress rejected =
                new EnterpriseProfileQueryService.AuditProgress(9100L, PHONE, "森特测试有限公司",
                        "91310000MA1FL0AB1X", "FILE-1", "REJECTED", "营业执照模糊，请重新上传", NOW, NOW - 86_400_000L);
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(true);
        when(profileQueryService.findByPhone(PHONE)).thenReturn(rejected);

        EnterpriseProfileQueryService.AuditProgress result = useCase.get(PHONE, CODE);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.auditRemark()).isEqualTo("营业执照模糊，请重新上传");
        assertThat(result.companyName()).isEqualTo("森特测试有限公司");
        assertThat(result.licenseFileId()).isEqualTo("FILE-1");
    }

    @Test
    void wrongSmsCodeRejectedWithoutQueryingProfile() {
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, "000000", NOW)).thenReturn(false);

        assertThatThrownBy(() -> useCase.get(PHONE, "000000"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SMS_CODE_INVALID);
        verify(profileQueryService, never()).findByPhone(anyString());
    }

    @Test
    void missingProfileThrowsNotFound() {
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(true);
        when(profileQueryService.findByPhone(PHONE)).thenReturn(null);

        assertThatThrownBy(() -> useCase.get(PHONE, CODE))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void blankPhoneRejectedBeforeConsumingCode() {
        assertThatThrownBy(() -> useCase.get("  ", CODE))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
        verify(smsCodeStore, never()).verifyAndConsume(anyString(), anyString(), anyString(), anyLong());
    }
}
