package com.sent.sports.common.interceptor;

import com.sent.sports.common.anno.PublicEndpoint;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PermissionChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 权限拦截器单测（P5-27）：方法注解优先、未登录 40101、权限不足 40301。
 * <p>2026-11-28 批次 1（P0-01）：{@code /api/admin/**} 默认拒绝——未标 {@link RequirePerm} 时
 * 必须是管理员身份，匿名与客户令牌一律 40301；公开端点用 {@link PublicEndpoint} 显式放行。</p>
 */
class PermissionInterceptorTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("方法级注解优先；持有权限则放行")
    void passesWhenPermissionHeld() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(checker("order:ship"));
        MockHttpServletRequest request = loggedIn(7L);

        assertThat(interceptor.preHandle(request, response, handler("ship"))).isTrue();
    }

    @Test
    @DisplayName("多个权限点需全部满足")
    void requiresAllPermissions() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(checker("order:ship"));

        assertThatThrownBy(() -> interceptor.preHandle(loggedIn(7L), response, handler("shipAndAudit")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问受保护接口 → UNAUTHENTICATED")
    void unauthenticatedIsRejected() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(checker("order:ship"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("ship")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("未标注注解的接口直接放行（含非 HandlerMethod 处理器）")
    void openEndpointPasses() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor((staffId, permission) -> false);

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, handler("open"))).isTrue();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isTrue();
    }

    @Test
    @DisplayName("类级 @RequirePerm 在方法未标注时生效")
    void classLevelAnnotationApplies() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor(checker("admin:read"));
        HandlerMethod handlerMethod = new HandlerMethod(new AdminHandler(),
                AdminHandler.class.getMethod("plain"));

        assertThat(interceptor.preHandle(loggedIn(7L), response, handlerMethod)).isTrue();
    }

    // ---------------- P0-01：/api/admin/** 默认拒绝 ----------------

    @Test
    @DisplayName("P0-01 匿名访问无注解的管理端接口 GET /api/admin/orders → FORBIDDEN(403)")
    void anonymousOnUnannotatedAdminPathIsForbidden() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor((staffId, permission) -> true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/orders");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("open")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("P0-01 客户令牌访问无注解的管理端接口 → FORBIDDEN(403)，不因非管理员而放行")
    void customerOnUnannotatedAdminPathIsForbidden() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor((staffId, permission) -> true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/customers");
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti-u"));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("open")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("P0-01 管理员令牌访问无注解的管理端接口仍放行（前端 22 个只读页依赖）")
    void adminOnUnannotatedAdminPathPasses() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor((staffId, permission) -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/orders");
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 9001L, "jti-a"));

        assertThat(interceptor.preHandle(request, response, handler("open"))).isTrue();
    }

    @Test
    @DisplayName("P0-01 @PublicEndpoint 显式放行：管理端路径上的公开接口匿名可访问")
    void publicEndpointOnAdminPathIsOpen() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor((staffId, permission) -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/public/ping");

        assertThat(interceptor.preHandle(request, response, handler("publicOpen"))).isTrue();
    }

    @Test
    @DisplayName("非管理端路径未注解仍放行（商品目录等公开读面不受影响）")
    void unannotatedPublicPathStillPasses() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor((staffId, permission) -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");

        assertThat(interceptor.preHandle(request, response, handler("open"))).isTrue();
    }

    private MockHttpServletRequest loggedIn(long staffId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, staffId, "jti-1"));
        return request;
    }

    private PermissionChecker checker(String... permissions) {
        Set<String> granted = Set.of(permissions);
        return (staffId, permission) -> granted.contains(permission);
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        DemoHandler bean = new DemoHandler();
        return new HandlerMethod(bean, DemoHandler.class.getMethod(methodName));
    }

    /** 测试用处理器：三种注解形态。 */
    static class DemoHandler {

        @RequirePerm("order:ship")
        public void ship() {
        }

        @RequirePerm({"order:ship", "order:audit"})
        public void shipAndAudit() {
        }

        public void open() {
        }

        /** 管理端路径上的公开端点：必须靠 @PublicEndpoint 才能匿名访问。 */
        @PublicEndpoint
        public void publicOpen() {
        }
    }

    @RequirePerm("admin:read")
    static class AdminHandler {

        public void plain() {
        }
    }
}
