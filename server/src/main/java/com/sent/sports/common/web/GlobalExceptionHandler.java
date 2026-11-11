package com.sent.sports.common.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：统一将异常转换为 {@link R} 响应体，禁止把堆栈返回前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static String traceId() {
        return MDC.get("traceId");
    }

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException ex, HttpServletResponse response) {
        response.setStatus(ex.getErrorCode().getHttpStatus());
        log.warn("biz error code={} msg={}", ex.getErrorCode().getCode(), ex.resolvedMessage());
        return R.<Void>fail(ex.getErrorCode().getCode(), ex.resolvedMessage()).setTraceId(traceId());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAM_INVALID.getDefaultMessage());
        return R.<Void>fail(ErrorCode.PARAM_INVALID.getCode(), msg).setTraceId(traceId());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleUnreadable(HttpMessageNotReadableException ex) {
        return R.<Void>fail(ErrorCode.PARAM_INVALID.getCode(), ErrorCode.PARAM_INVALID.getDefaultMessage())
                .setTraceId(traceId());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public R<Void> handleForbidden(AccessDeniedException ex) {
        return R.<Void>fail(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage())
                .setTraceId(traceId());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("unexpected error path={}", request.getRequestURI(), ex);
        return R.<Void>fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getDefaultMessage())
                .setTraceId(traceId());
    }
}
