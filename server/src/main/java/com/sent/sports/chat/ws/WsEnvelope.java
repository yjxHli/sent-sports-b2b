package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.Set;

/**
 * 客服 WebSocket 消息信封（P5-35）：进程内 / 前后端之间的统一报文格式。
 * <p>纯 POJO（record），不依赖 Spring，也不带任何 Spring 注解；{@link #parse(String)} / {@link #toJson()}
 * 使用自带 ObjectMapper，与 Spring 容器中的 ObjectMapper（其 Long 会被序列化为字符串）相互独立，
 * 保证数值字段在本信封内始终是 JSON number，可无损往返。</p>
 * <p>{@code type} 仅允许 {@link #MSG} / {@link #ACK} / {@link #READ} / {@link #QUEUE} 四种，
 * 非法类型或坏 JSON 一律抛 {@link BizException}({@link ErrorCode#PARAM_INVALID})。</p>
 */
public record WsEnvelope(String type, long sessionId, long seq, String payload, long timestamp) {

    public static final String MSG = "MSG";
    public static final String ACK = "ACK";
    public static final String READ = "READ";
    public static final String QUEUE = "QUEUE";

    private static final Set<String> VALID_TYPES = Set.of(MSG, ACK, READ, QUEUE);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 将 JSON 文本反序列化为信封；坏 JSON / 非法 type 抛 PARAM_INVALID。 */
    public static WsEnvelope parse(String json) {
        WsEnvelope envelope;
        try {
            envelope = MAPPER.readValue(json, WsEnvelope.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (envelope == null || !isValidType(envelope.type())) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return envelope;
    }

    /** 将本信封序列化为 JSON 文本。 */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
    }

    public static WsEnvelope msg(long sessionId, long seq, String payload) {
        return of(MSG, sessionId, seq, payload);
    }

    public static WsEnvelope ack(long sessionId, long seq, String payload) {
        return of(ACK, sessionId, seq, payload);
    }

    public static WsEnvelope read(long sessionId, long seq, String payload) {
        return of(READ, sessionId, seq, payload);
    }

    public static WsEnvelope queue(long sessionId, long seq, String payload) {
        return of(QUEUE, sessionId, seq, payload);
    }

    private static WsEnvelope of(String type, long sessionId, long seq, String payload) {
        return new WsEnvelope(type, sessionId, seq, payload, System.currentTimeMillis());
    }

    private static boolean isValidType(String type) {
        return type != null && VALID_TYPES.contains(type);
    }
}
