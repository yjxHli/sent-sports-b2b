package com.sent.sports.common.interceptor;

import com.sent.sports.common.anno.Idempotent;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.idempotent.IdempotencyStore;
import com.sent.sports.common.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * 幂等拦截器（P5-29，plan §7.12）：仅协调状态机，存储走 {@link IdempotencyStore} 端口。
 * <ol>
 *   <li>缺/非法 {@code Idempotency-Key} → 40002；</li>
 *   <li>首次占用成功 → 放行，并记录键；</li>
 *   <li>键为 PROCESSING → 40902；</li>
 *   <li>键为 DONE 且 replayResult → 回放首次响应 + {@code X-Idempotent-Replay: true}；</li>
 *   <li>成功 → 写 DONE 快照；业务异常 → 删键（可重试）；系统异常 → 保留键（防重复副作用）。</li>
 * </ol>
 */
public class IdempotentInterceptor implements HandlerInterceptor {

    /** 首次占用成功时记录的键，供 afterCompletion 写入/清理。 */
    public static final String KEY_ATTR = "com.sent.sports.idemKey";
    /** 响应快照 JSON（由 ResponseBodyAdvice 在响应写出时挂到该属性）。 */
    public static final String RESULT_ATTR = "com.sent.sports.idemResult";
    private static final String TTL_ATTR = "com.sent.sports.idemTtl";
    private static final String HEADER = "Idempotency-Key";
    private static final int MIN_KEY_LEN = 8;
    private static final int MAX_KEY_LEN = 64;

    private final IdempotencyStore store;

    public IdempotentInterceptor(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Idempotent annotation = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (annotation == null) {
            return true;
        }
        String headerKey = request.getHeader(HEADER);
        if (headerKey == null || headerKey.length() < MIN_KEY_LEN || headerKey.length() > MAX_KEY_LEN) {
            throw new BizException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }
        String key = "idem:" + annotation.scene() + ":" + principalOf(request) + ":" + headerKey;
        Duration ttl = Duration.ofSeconds(annotation.ttlSeconds());

        if (store.tryStart(key, ttl)) {
            request.setAttribute(KEY_ATTR, key);
            request.setAttribute(TTL_ATTR, annotation.ttlSeconds());
            return true;
        }
        if (store.isProcessing(key)) {
            throw new BizException(ErrorCode.DUPLICATE_PROCESSING);
        }
        Optional<String> done = store.doneResult(key);
        if (done.isPresent() && annotation.replayResult()) {
            response.setStatus(200);
            response.setHeader("X-Idempotent-Replay", "true");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(done.get());
            return false;
        }
        throw new BizException(ErrorCode.DUPLICATE_PROCESSING);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object keyAttr = request.getAttribute(KEY_ATTR);
        if (!(keyAttr instanceof String key)) {
            return;
        }
        long ttlSeconds = ttlOf(request);
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        if (ex == null) {
            Object snapshot = request.getAttribute(RESULT_ATTR);
            if (snapshot instanceof String json) {
                store.complete(key, json, ttl);
            }
        } else if (ex instanceof BizException) {
            store.release(key); // 参数/业务校验失败：允许改后重试
        }
        // 系统异常/超时：保留键，重试走重放而非重复副作用
    }

    private long ttlOf(HttpServletRequest request) {
        Object ttl = request.getAttribute(TTL_ATTR);
        return ttl instanceof Integer i ? i : 600;
    }

    private String principalOf(HttpServletRequest request) {
        Object attr = request.getAttribute(CurrentUser.ATTR);
        if (attr instanceof CurrentUser user) {
            return String.valueOf(user.userId());
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "anonymous" : remote;
    }
}
