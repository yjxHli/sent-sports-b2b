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
 * <p>{@code type} 仅允许 {@link #MSG} / {@link #ACK} / {@link #READ} / {@link #QUEUE} /
 * {@link #ASSIGNED} / {@link #TYPING} 六种，
 * 非法类型或坏 JSON 一律抛 {@link BizException}({@link ErrorCode#PARAM_INVALID})。</p>
 */
public record WsEnvelope(String type, long sessionId, long seq, String payload, long timestamp) {

    public static final String MSG = "MSG";
    public static final String ACK = "ACK";
    public static final String READ = "READ";
    public static final String QUEUE = "QUEUE";
    /** 服务端主动通知：会话已被客服接入（用户端据此隐藏排队提示并解锁输入）。 */
    public static final String ASSIGNED = "ASSIGNED";
    /** 「正在输入」：只在两端之间转发，不落库、不进消息列表。 */
    public static final String TYPING = "TYPING";

    private static final Set<String> VALID_TYPES = Set.of(MSG, ACK, READ, QUEUE, ASSIGNED, TYPING);

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

    /** 会话被接入通知（服务端 → 客户）。 */
    public static WsEnvelope assigned(long sessionId, String payload) {
        return of(ASSIGNED, sessionId, 0L, payload);
    }

    /** 「正在输入」提示（双向：客户打字 → 客服端；客服打字 → 用户端）。 */
    public static WsEnvelope typing(long sessionId, String payload) {
        return of(TYPING, sessionId, 0L, payload);
    }

    private static WsEnvelope of(String type, long sessionId, long seq, String payload) {
        return new WsEnvelope(type, sessionId, seq, payload, System.currentTimeMillis());
    }

    private static boolean isValidType(String type) {
        return type != null && VALID_TYPES.contains(type);
    }
}
