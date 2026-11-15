package com.sent.sports.chat.ws;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 信封编解码单元测试（P5-35）。 */
class WsEnvelopeTest {

    @Test
    void fourTypesRoundTrip() {
        WsEnvelope[] envelopes = {
                WsEnvelope.msg(101L, 1L, "hello"),
                WsEnvelope.ack(101L, 1L, "ok"),
                WsEnvelope.read(101L, 2L, "read"),
                WsEnvelope.queue(101L, 0L, "queued")
        };
        assertThat(envelopes[0].type()).isEqualTo(WsEnvelope.MSG);
        assertThat(envelopes[1].type()).isEqualTo(WsEnvelope.ACK);
        assertThat(envelopes[2].type()).isEqualTo(WsEnvelope.READ);
        assertThat(envelopes[3].type()).isEqualTo(WsEnvelope.QUEUE);

        for (WsEnvelope original : envelopes) {
            WsEnvelope decoded = WsEnvelope.parse(original.toJson());
            assertThat(decoded).isEqualTo(original);
        }
    }

    @Test
    void assignedTypeRoundTrip() {
        WsEnvelope assigned = WsEnvelope.assigned(101L, "{\"event\":\"ASSIGNED\",\"staffId\":9001}");

        assertThat(assigned.type()).isEqualTo(WsEnvelope.ASSIGNED);
        assertThat(WsEnvelope.parse(assigned.toJson())).isEqualTo(assigned);
    }

    /** 「正在输入」帧：双向提示专用，必须是合法类型且能无损往返。 */
    @Test
    void typingTypeRoundTrip() {
        WsEnvelope typing = WsEnvelope.typing(101L, "{\"typing\":true}");

        assertThat(typing.type()).isEqualTo(WsEnvelope.TYPING);
        assertThat(WsEnvelope.parse(typing.toJson())).isEqualTo(typing);
    }

    @Test
    void roundTripPreservesAllFields() {
        WsEnvelope original = new WsEnvelope(WsEnvelope.MSG, 999L, 42L, "payload-中文", 123456789L);

        WsEnvelope decoded = WsEnvelope.parse(original.toJson());

        assertThat(decoded.type()).isEqualTo(WsEnvelope.MSG);
        assertThat(decoded.sessionId()).isEqualTo(999L);
        assertThat(decoded.seq()).isEqualTo(42L);
        assertThat(decoded.payload()).isEqualTo("payload-中文");
        assertThat(decoded.timestamp()).isEqualTo(123456789L);
    }

    @Test
    void badJsonThrowsParamInvalid() {
        assertThatThrownBy(() -> WsEnvelope.parse("{not-json"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void illegalTypeThrowsParamInvalid() {
        WsEnvelope bogus = new WsEnvelope("BOGUS", 1L, 2L, "x", 3L);

        assertThatThrownBy(() -> WsEnvelope.parse(bogus.toJson()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));
    }
}
