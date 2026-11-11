package com.sent.sports.common.service.impl;

import com.sent.sports.common.dto.admin.ConfirmDtos;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 确认令牌服务单测（P5-31）：短语校验、按动作+目标绑定复用、用后即焚、过期失效。 */
class ConfirmTokenServiceImplTest {

    private MutableClock clock;
    private ConfirmTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-04T02:00:00Z"));
        service = new ConfirmTokenServiceImpl(new InMemoryConfirmTokenStore(clock), clock);
    }

    @Test
    @DisplayName("短语正确时签发令牌，有效期 5 分钟")
    void issueReturnsTokenWithTtl() {
        ConfirmDtos.TokenVO token = service.issue(new ConfirmDtos.ConfirmCmd("refund:audit", "R0001", "CONFIRM"));

        assertThat(token.token()).isNotBlank();
        assertThat(token.expireAt()).isEqualTo(clock.instant().toEpochMilli() + Duration.ofMinutes(5).toMillis());
    }

    @Test
    @DisplayName("短语错误抛 CONFIRM_TOKEN_INVALID")
    void wrongPhraseIsRejected() {
        assertThatThrownBy(() -> service.issue(new ConfirmDtos.ConfirmCmd("refund:audit", "R0001", "confirm")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFIRM_TOKEN_INVALID);
    }

    @Test
    @DisplayName("标准动作走 ConfirmAction 规范短语（PAYMENT_WRITE_OFF → 确认核销），不再要求 CONFIRM")
    void enumActionUsesCanonicalPhrase() {
        ConfirmDtos.TokenVO token = service.issue(
                new ConfirmDtos.ConfirmCmd("PAYMENT_WRITE_OFF", "PR20261107000001", "确认核销"));
        assertThat(token.token()).isNotBlank();

        assertThatThrownBy(() -> service.issue(
                new ConfirmDtos.ConfirmCmd("PAYMENT_WRITE_OFF", "PR20261107000002", "CONFIRM")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFIRM_TOKEN_INVALID);
    }

    @Test
    @DisplayName("同一动作+目标在有效期内复用同一令牌；换目标则换令牌")
    void tokenIsBoundToActionAndTarget() {
        ConfirmDtos.TokenVO first = service.issue(new ConfirmDtos.ConfirmCmd("refund:audit", "R0001", "CONFIRM"));
        ConfirmDtos.TokenVO again = service.issue(new ConfirmDtos.ConfirmCmd("refund:audit", "R0001", "CONFIRM"));
        ConfirmDtos.TokenVO otherTarget = service.issue(new ConfirmDtos.ConfirmCmd("refund:audit", "R0002", "CONFIRM"));

        assertThat(again.token()).isEqualTo(first.token());
        assertThat(otherTarget.token()).isNotEqualTo(first.token());
    }

    @Test
    @DisplayName("用后即焚：令牌只能消费一次")
    void tokenIsSingleUse() {
        ConfirmDtos.TokenVO token = service.issue(new ConfirmDtos.ConfirmCmd("finance:reverse", "P0001", "CONFIRM"));

        assertThat(service.consume("finance:reverse", "P0001", token.token())).isTrue();
        assertThat(service.consume("finance:reverse", "P0001", token.token())).isFalse();
    }

    @Test
    @DisplayName("用错目标或错令牌不会消耗有效令牌")
    void wrongTargetOrTokenDoesNotConsume() {
        ConfirmDtos.TokenVO token = service.issue(new ConfirmDtos.ConfirmCmd("finance:reverse", "P0001", "CONFIRM"));

        assertThat(service.consume("finance:reverse", "P0002", token.token())).isFalse();
        assertThat(service.consume("finance:reverse", "P0001", "not-the-token")).isFalse();
        assertThat(service.consume("finance:reverse", "P0001", token.token())).isTrue();
    }

    @Test
    @DisplayName("过期令牌不可消费，重新签发得到新令牌")
    void expiredTokenIsInvalid() {
        ConfirmDtos.TokenVO token = service.issue(new ConfirmDtos.ConfirmCmd("stock:check", "C0001", "CONFIRM"));
        clock.advance(Duration.ofMinutes(6));

        assertThat(service.consume("stock:check", "C0001", token.token())).isFalse();
        ConfirmDtos.TokenVO renewed = service.issue(new ConfirmDtos.ConfirmCmd("stock:check", "C0001", "CONFIRM"));
        assertThat(renewed.token()).isNotEqualTo(token.token());
    }

    @Test
    @DisplayName("可按动作覆盖确认短语")
    void phraseCanBeOverriddenPerAction() {
        ConfirmTokenServiceImpl custom = new ConfirmTokenServiceImpl(
                new InMemoryConfirmTokenStore(clock), clock, Map.of("order:cancel", "CANCEL-OK"));

        ConfirmDtos.TokenVO token = custom.issue(new ConfirmDtos.ConfirmCmd("order:cancel", "O0001", "CANCEL-OK"));
        assertThat(token.token()).isNotBlank();
        assertThatThrownBy(() -> custom.issue(new ConfirmDtos.ConfirmCmd("order:cancel", "O0001", "CONFIRM")))
                .isInstanceOf(BizException.class);
        // 未配置的动作仍走统一短语
        assertThat(custom.issue(new ConfirmDtos.ConfirmCmd("other:action", "X", "CONFIRM")).token()).isNotBlank();
    }

    /** 可推进的测试时钟。 */
    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
