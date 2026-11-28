package com.sent.sports.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.common.interceptor.IdempotentInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 幂等响应快照切面（2026-11-28 批次 2 / P1-01）。
 *
 * <p><b>为什么要有它</b>：{@code IdempotentInterceptor} 在 {@code afterCompletion} 里读
 * {@link IdempotentInterceptor#RESULT_ATTR} 落缓存，但全仓没有任何组件写这个属性，
 * 导致 {@code store.complete()} 永不执行——键一直停在 PROCESSING，重试只会拿到 40902，
 * 「回放首次响应」的能力等于不存在。</p>
 *
 * <p><b>快照口径</b>：只缓存成功响应（{@code R.code == 0}）。业务失败（40002/40901 等）不写快照，
 * 由拦截器释放键，允许调用方修正后重试；否则一次参数错误会被缓存 10 分钟，用户改对了也提交不进去。</p>
 *
 * <p><b>零开销</b>：只有拦截器真正开启幂等作用域（写了 {@code KEY_ATTR}）的请求才会序列化，
 * 普通请求只多一次属性读。</p>
 */
@RestControllerAdvice
public class IdempotentResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(IdempotentResponseAdvice.class);

    private final ObjectMapper objectMapper;

    public IdempotentResponseAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 不在这里判断：写不写快照取决于「本次请求是否进入幂等作用域」，与返回类型无关
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || !(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        HttpServletRequest req = servletRequest.getServletRequest();
        if (req.getAttribute(IdempotentInterceptor.KEY_ATTR) == null) {
            return body;
        }
        if (!isSuccessful(body)) {
            // 失败响应不缓存：拦截器据此释放键，允许重试
            return body;
        }
        try {
            req.setAttribute(IdempotentInterceptor.RESULT_ATTR, objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            // 快照失败不能连累业务响应：本次退化为「不回放」，键由拦截器按无快照处理
            log.warn("幂等响应快照序列化失败，本次不回放: {}", e.getMessage());
        }
        return body;
    }

    /** 成功口径：统一响应体看 code，非统一响应体（文件流等）一律视为成功。 */
    private static boolean isSuccessful(Object body) {
        return !(body instanceof R<?> r) || r.getCode() == 0;
    }
}
