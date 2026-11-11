package com.sent.sports.common.error;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * 业务异常：携带 {@link ErrorCode} 与可选的格式化参数。
 * 由 {@link com.sent.sports.common.web.GlobalExceptionHandler} 统一转换为响应体。
 */
public final class BizException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final Object[] args;

    public BizException(ErrorCode errorCode) {
        this(errorCode, (Object[]) null);
    }

    public BizException(ErrorCode errorCode, Object... args) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = args;
    }

    public BizException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }

    /** 经过参数占位替换后的最终文案；无参数时返回默认文案。 */
    public String resolvedMessage() {
        if (args == null || args.length == 0) {
            return errorCode.getDefaultMessage();
        }
        String defaultMessage = errorCode.getDefaultMessage();
        // 项目里大量使用 new BizException(code, "具体业务原因")，而错误码默认文案没有 %s 占位符，
        // String.format 会静默丢弃这个参数，前端只能看到泛泛的「状态冲突」而无法定位 409 真因。
        // 单个字符串参数且无占位符时，把它当作完整原因直接透传；其余情况保持原格式化语义。
        if (args.length == 1 && args[0] instanceof CharSequence
                && !defaultMessage.contains("%s")) {
            return args[0].toString();
        }
        try {
            return String.format(defaultMessage, Arrays.stream(args)
                    .map(Objects::toString)
                    .toArray());
        } catch (RuntimeException e) {
            return defaultMessage;
        }
    }
}
