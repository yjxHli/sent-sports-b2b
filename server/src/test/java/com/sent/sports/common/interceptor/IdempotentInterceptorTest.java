package com.sent.sports.common.interceptor;

import com.sent.sports.common.anno.Idempotent;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.idempotent.IdempotencyStore;
import com.sent.sports.common.web.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 幂等拦截器单元测试（P5-29）：缺头 40002、首次放行、处理中 40902、DONE 重放、
 * 业务异常删键、系统异常保留键。以伪造 {@link IdempotencyStore} 验证状态机。
 */
class IdempotentInterceptorTest {

    static class DummyController {
        @Idempotent(scene = "ORDER_CREATE")
        public void createOrder() {
        }

        @Idempotent(scene = "ORDER_CREATE", replayResult = false)
        public void createOrderNoReplay() {
        }

        public void plain() {
        }
    }

    /** 伪造幂等存储：可控 tryStart / processing / done。 */
    static class FakeStore implements IdempotencyStore {
        boolean startAccepted = true;
        boolean processing = false;
        String doneJson;
        boolean released = false;
        boolean completed = false;
        String completedJson;
        Duration completedTtl;
        String key;

        @Override
        public boolean tryStart(String key, Duration ttl) {
            this.key = key;
            return startAccepted;
        }

        @Override
        public boolean isProcessing(String key) {
            return processing;
        }

        @Override
        public Optional<String> doneResult(String key) {
            return Optional.ofNullable(doneJson);
        }

        @Override
        public void complete(String key, String responseJson, Duration ttl) {
            this.completed = true;
            this.completedJson = responseJson;
            this.completedTtl = ttl;
        }

        @Override
        public void release(String key) {
            this.released = true;
        }
    }

    private HandlerMethod handlerOf(String name) throws NoSuchMethodException {
        Method m = DummyController.class.getMethod(name);
        return new HandlerMethod(new DummyController(), m);
    }

    private MockHttpServletRequest request(String uri, String idemHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setAttribute(CurrentUser.ATTR, new CurrentUser("user", 10086L, "j"));
        if (idemHeader != null) {
            req.addHeader("Idempotency-Key", idemHeader);
        }
        return req;
    }

    @Test
    void missingHeaderReturns40002() throws Exception {
        IdempotentInterceptor interceptor = new IdempotentInterceptor(new FakeStore());
        assertThatThrownBy(() -> interceptor.preHandle(request("/api/orders", null),
                new MockHttpServletResponse(), handlerOf("createOrder")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_MISSING));
    }

    @Test
    void newRequestProceedsAndBuildsKey() throws Exception {
        FakeStore store = new FakeStore();
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store);
        MockHttpServletRequest req = request("/api/orders", "abc12345");

        boolean proceed = interceptor.preHandle(req, new MockHttpServletResponse(), handlerOf("createOrder"));

        assertThat(proceed).isTrue();
        assertThat(store.key).isEqualTo("idem:ORDER_CREATE:10086:abc12345");
        assertThat(req.getAttribute(IdempotentInterceptor.KEY_ATTR)).isEqualTo("idem:ORDER_CREATE:10086:abc12345");
    }

    @Test
    void duplicateProcessingReturns40902() throws Exception {
        FakeStore store = new FakeStore();
        store.startAccepted = false;
        store.processing = true;
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store);

        assertThatThrownBy(() -> interceptor.preHandle(request("/api/orders", "abc12345"),
                new MockHttpServletResponse(), handlerOf("createOrder")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_PROCESSING));
    }

    @Test
    void doneReplaysCachedResponse() throws Exception {
        FakeStore store = new FakeStore();
        store.startAccepted = false;
        store.doneJson = "{\"code\":0,\"data\":{\"orderNo\":\"SO-1\"}}";
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store);
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request("/api/orders", "abc12345"), res, handlerOf("createOrder"));

        assertThat(proceed).isFalse();
        assertThat(res.getHeader("X-Idempotent-Replay")).isEqualTo("true");
        assertThat(res.getContentAsString()).contains("SO-1");
    }

    @Test
    void doneWithoutReplayReturns40902() throws Exception {
        FakeStore store = new FakeStore();
        store.startAccepted = false;
        store.doneJson = "{\"code\":0}";
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store);

        assertThatThrownBy(() -> interceptor.preHandle(request("/api/orders", "abc12345"),
                new MockHttpServletResponse(), handlerOf("createOrderNoReplay")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_PROCESSING));
    }

    @Test
    void successCompletesSnapshot() throws Exception {
        FakeStore store = new FakeStore();
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store);
        MockHttpServletRequest req = request("/api/orders", "abc12345");
        interceptor.preHandle(req, new MockHttpServletResponse(), handlerOf("createOrder"));
        req.setAttribute(IdempotentInterceptor.RESULT_ATTR, "{\"code\":0}");

        interceptor.afterCompletion(req, new MockHttpServletResponse(), handlerOf("createOrder"), null);

        assertThat(store.completed).isTrue();
        assertThat(store.completedJson).isEqualTo("{\"code\":0}");
        assertThat(store.released).isFalse();
    }

    @Test
    void bizExceptionReleasesKey() throws Exception {
        FakeStore store = new FakeStore();
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store);
        MockHttpServletRequest req = request("/api/orders", "abc12345");
        interceptor.preHandle(req, new MockHttpServletResponse(), handlerOf("createOrder"));

        interceptor.afterCompletion(req, new MockHttpServletResponse(), handlerOf("createOrder"),
                new BizException(ErrorCode.STOCK_NOT_ENOUGH));

        assertThat(store.released).isTrue();
        assertThat(store.completed).isFalse();
    }

    @Test
    void systemExceptionKeepsKey() throws Exception {
        FakeStore store = new FakeStore();
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store);
        MockHttpServletRequest req = request("/api/orders", "abc12345");
        interceptor.preHandle(req, new MockHttpServletResponse(), handlerOf("createOrder"));

        interceptor.afterCompletion(req, new MockHttpServletResponse(), handlerOf("createOrder"),
                new RuntimeException("boom"));

        assertThat(store.released).isFalse();
        assertThat(store.completed).isFalse();
    }
}
