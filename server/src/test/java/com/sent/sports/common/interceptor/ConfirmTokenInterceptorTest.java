package com.sent.sports.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.service.impl.ConfirmTokenServiceImpl;
import com.sent.sports.common.service.impl.InMemoryConfirmTokenStore;
import com.sent.sports.common.web.ConfirmBodyCacheFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 确认令牌拦截器（R10）：替换 14 处复制粘贴后，安全语义一条都不能少——
 * 缺令牌 40302、令牌与目标不匹配 40302、目标按 BatchIds 归一口径、用后即焚。
 */
class ConfirmTokenInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private ConfirmTokenInterceptor interceptor() {
        ConfirmTokenService service =
                new ConfirmTokenServiceImpl(new InMemoryConfirmTokenStore(Clock.systemUTC()), Clock.systemUTC());
        return new ConfirmTokenInterceptor(service, MAPPER);
    }

    private static String issue(ConfirmTokenService service, ConfirmAction action, String target) {
        return service.issue(new com.sent.sports.common.dto.admin.ConfirmDtos.ConfirmCmd(
                action.name(), target, action.phrase())).token();
    }

    @Test
    @DisplayName("缺少 X-Confirm-Token → 40302，业务方法不得进入")
    void missingTokenRejected() throws Exception {
        assertThatThrownBy(() -> interceptor().preHandle(
                new MockHttpServletRequest(), response, handler("batchDelete")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFIRM_TOKEN_INVALID);
    }

    @Test
    @DisplayName("令牌与目标不匹配 → 40302")
    void tokenBoundToTarget() throws Exception {
        ConfirmTokenServiceImpl service = new ConfirmTokenServiceImpl(
                new InMemoryConfirmTokenStore(Clock.systemUTC()), Clock.systemUTC());
        String token = issue(service, ConfirmAction.BATCH_DELETE, "101,102");
        ConfirmTokenInterceptor interceptor = new ConfirmTokenInterceptor(service, MAPPER);

        assertThatThrownBy(() -> interceptor.preHandle(
                requestWithIds(token, "101", "103"), response, handler("batchDelete")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFIRM_TOKEN_INVALID);
    }

    @Test
    @DisplayName("批量删除：目标按 trim→去空→去重→升序 归一化后消费，令牌用后即焚")
    void batchDeleteNormalizesTargetAndBurnsToken() throws Exception {
        ConfirmTokenServiceImpl service = new ConfirmTokenServiceImpl(
                new InMemoryConfirmTokenStore(Clock.systemUTC()), Clock.systemUTC());
        String token = issue(service, ConfirmAction.BATCH_DELETE, "101,102");
        ConfirmTokenInterceptor interceptor = new ConfirmTokenInterceptor(service, MAPPER);

        MockHttpServletRequest request = requestWithIds(token, " 102 ", "101", "102", "");
        assertThat(interceptor.preHandle(request, response, handler("batchDelete"))).isTrue();
        assertThat(request.getAttribute(ConfirmTokenInterceptor.TARGET_ATTR)).isEqualTo("101,102");

        // 二次提交同一令牌必须失败（用后即焚）
        assertThatThrownBy(() -> interceptor.preHandle(
                requestWithIds(token, "101", "102"), response, handler("batchDelete")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFIRM_TOKEN_INVALID);
    }

    @Test
    @DisplayName("路径变量目标：关单按订单号绑定")
    void pathVariableTarget() throws Exception {
        ConfirmTokenServiceImpl service = new ConfirmTokenServiceImpl(
                new InMemoryConfirmTokenStore(Clock.systemUTC()), Clock.systemUTC());
        String token = issue(service, ConfirmAction.ORDER_CLOSE, "SO-100");
        ConfirmTokenInterceptor interceptor = new ConfirmTokenInterceptor(service, MAPPER);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/orders/SO-100/close");
        request.addHeader("X-Confirm-Token", token);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "SO-100"));

        assertThat(interceptor.preHandle(request, response, handler("close"))).isTrue();
    }

    @Test
    @DisplayName("未标注 @Confirm 的方法与非 HandlerMethod 处理器一律放行")
    void unannotatedPasses() throws Exception {
        ConfirmTokenInterceptor interceptor = interceptor();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, handler("open"))).isTrue();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isTrue();
    }

    private MockHttpServletRequest requestWithIds(String token, String... ids) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/orders/batch-delete");
        request.addHeader("X-Confirm-Token", token);
        String body = MAPPER.writeValueAsString(Map.of("ids", ids));
        // 与 ConfirmBodyCacheFilter 同一契约：拦截器只读预读好的字节，不自己消费请求流
        request.setAttribute(ConfirmBodyCacheFilter.BODY_ATTR, body.getBytes(StandardCharsets.UTF_8));
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        DemoHandler bean = new DemoHandler();
        return new HandlerMethod(bean, DemoHandler.class.getMethod(methodName));
    }

    /** 测试用处理器：批量删除（请求体 ids）与关单（路径变量）两种目标来源。 */
    static class DemoHandler {

        @Confirm(action = ConfirmAction.BATCH_DELETE)
        public void batchDelete() {
        }

        @Confirm(action = ConfirmAction.ORDER_CLOSE, target = Confirm.Target.PATH_VARIABLE, field = "id")
        public void close() {
        }

        public void open() {
        }
    }
}
