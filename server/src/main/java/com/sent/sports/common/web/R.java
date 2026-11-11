package com.sent.sports.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 统一响应体：{code, message, data, traceId}。
 * 失败时 code 为业务错误码（见 {@link com.sent.sports.common.error.ErrorCode}），成功为 0。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功码 */
    public static final int SUCCESS = 0;

    private int code;
    private String message;
    private T data;
    private String traceId;

    public R() {
    }

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(SUCCESS, "ok", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, "ok", data);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(SUCCESS, message, data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /** 失败时返回业务错误码 + 文案（无 data） */
    public static <T> R<T> fail(int code, String message, T data) {
        return new R<>(code, message, data);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    public R<T> setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
