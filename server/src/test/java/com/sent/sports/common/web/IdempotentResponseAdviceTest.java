package com.sent.sports.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.common.interceptor.IdempotentInterceptor;
import com.sent.sports.common.web.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 幂等响应快照切面单测（2026-11-28 批次 2 / P1-01）。
 *
 * <p>背景：{@code IdempotentInterceptor} 早期版本在 afterCompletion 里读
 * {@code RESULT_ATTR}，但全仓没有任何 {@code ResponseBodyAdvice} 写这个属性，
 * 于是 {@code store.complete()} 永不调用——前端发出去的 {@code Idempotency-Key}
 * 只会换回 40902「处理中」，回放一次结果的能力等于不存在。</p>
 *
 * <p>本切面只做一件事：**已经进入幂等作用域**（拦截器写了 KEY_ATTR）的请求，
 * 把响应体序列化后挂到 RESULT_ATTR，交给拦截器在请求结束时落缓存。</p>
 */
class IdempotentResponseAdviceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IdempotentResponseAdvice advice = new IdempotentResponseAdvice(objectMapper);

    private MethodParameter returnType() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("ok");
        return new MethodParameter(method, -1);
    }

    @Test
    @DisplayName("已进入幂等作用域：响应体被序列化挂到 RESULT_ATTR")
    void snapshotsBodyWhenIdempotentScopeActive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(IdempotentInterceptor.KEY_ATTR, "idem:ORDER_CREATE:9002:abc12345");
        R<String> body = R.ok("SO123");

        advice.beforeBodyWrite(body, returnType(), MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(request.getAttribute(IdempotentInterceptor.RESULT_ATTR))
                .as("快照必须是可回放的 JSON")
                .isInstanceOf(String.class)
                .asString()
                .contains("\"code\":0")
                .contains("SO123");
    }

    @Test
    @DisplayName("未进入幂等作用域：不写任何属性，普通请求零开销")
    void ignoresRequestWithoutIdempotentScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        advice.beforeBodyWrite(R.ok("x"), returnType(), MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(request.getAttribute(IdempotentInterceptor.RESULT_ATTR)).isNull();
    }

    @Test
    @DisplayName("空响应体（204 等）：不写快照，避免把 null 缓存成 DONE")
    void ignoresNullBody() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(IdempotentInterceptor.KEY_ATTR, "idem:x:1:abcdefgh");

        Object result = advice.beforeBodyWrite(null, null, MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(result).isNull();
        assertThat(request.getAttribute(IdempotentInterceptor.RESULT_ATTR)).isNull();
    }

    @Test
    @DisplayName("序列化失败不能让业务响应失败：吞掉异常、只留告警")
    void serializationFailureDoesNotBreakResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(IdempotentInterceptor.KEY_ATTR, "idem:x:1:abcdefgh");
        Object cyclic = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this;
            }
        };

        Object result = advice.beforeBodyWrite(cyclic, returnType(), MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(result).isSameAs(cyclic);
        assertThat(request.getAttribute(IdempotentInterceptor.RESULT_ATTR)).isNull();
    }

    /** 仅供取 MethodParameter 用。 */
    static class SampleController {
        public R<String> ok() {
            return R.ok();
        }
    }
}
