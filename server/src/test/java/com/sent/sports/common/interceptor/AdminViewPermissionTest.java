package com.sent.sports.common.interceptor;

import com.sent.sports.chat.controller.admin.AdminChatController;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PermissionChecker;
import com.sent.sports.customer.controller.admin.AdminCustomerController;
import com.sent.sports.customize.controller.admin.AdminCustomController;
import com.sent.sports.finance.controller.admin.AdminFinanceController;
import com.sent.sports.order.controller.admin.AdminOrderController;
import com.sent.sports.product.controller.admin.AdminProductController;
import com.sent.sports.refund.controller.admin.AdminRefundController;
import com.sent.sports.stock.controller.admin.AdminStockController;
import com.sent.sports.system.controller.admin.AdminMeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1-RBAC：审计报告列出的 22 个管理端只读接口必须声明具体 {@code xxx:view} 权限点，
 * 且「无权限员工 → FORBIDDEN / 有权限员工 → 放行（即请求可返回 200）」。
 * <p>这里直接对真实 Controller 的方法反射取注解，再交给真实 {@link PermissionInterceptor} 判定，
 * 避免「测试里自己造一个带注解的假方法」导致漏检。</p>
 */
class AdminViewPermissionTest {

    private record Endpoint(Class<?> controller, String method, String permission) {
        @Override
        public String toString() {
            return controller.getSimpleName() + "." + method + " → " + permission;
        }
    }

    /** 审计报告 §2.1 的 22 个 GET（行号口径见 docs/全量深度巡检与安全审计报告-2026-11-28.md）。 */
    static Stream<Arguments> endpoints() {
        return endpointsToCheck().map(e -> Arguments.of(e));
    }

    private static Stream<Endpoint> endpointsToCheck() {
        return Stream.of(
                ep(AdminOrderController.class, "list", "order:view"),
                ep(AdminOrderController.class, "detail", "order:view"),
                ep(AdminOrderController.class, "customerOrders", "order:view"),

                ep(AdminFinanceController.class, "receivables", "finance:view"),
                ep(AdminFinanceController.class, "invoices", "finance:view"),
                ep(AdminFinanceController.class, "invoicePdf", "finance:view"),
                ep(AdminFinanceController.class, "contracts", "finance:view"),
                ep(AdminFinanceController.class, "paymentRecords", "finance:view"),

                ep(AdminCustomerController.class, "list", "customer:view"),
                ep(AdminCustomerController.class, "detail", "customer:view"),

                ep(AdminChatController.class, "sessions", "chat:view"),
                ep(AdminChatController.class, "openSessions", "chat:view"),
                ep(AdminChatController.class, "workload", "chat:view"),
                ep(AdminChatController.class, "todayStats", "chat:view"),

                ep(AdminCustomController.class, "pending", "custom:view"),
                ep(AdminCustomController.class, "detail", "custom:view"),

                ep(AdminRefundController.class, "list", "refund:view"),
                ep(AdminRefundController.class, "detail", "refund:view"),

                ep(AdminProductController.class, "list", "product:view"),

                ep(AdminStockController.class, "list", "stock:view"),
                ep(AdminStockController.class, "flows", "stock:view"),

                ep(AdminMeController.class, "me", "system:me:view")
        );
    }

    private static Endpoint ep(Class<?> controller, String method, String permission) {
        return new Endpoint(controller, method, permission);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("P1 22 个只读接口：无 view 权限 → 403，有 view 权限 → 放行返回 200")
    void viewPermissionIsDeclaredAndEnforced(Endpoint endpoint) throws Exception {
        Method method = Arrays.stream(endpoint.controller().getDeclaredMethods())
                .filter(m -> m.getName().equals(endpoint.method()) && m.isAnnotationPresent(GetMapping.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 GET 方法 " + endpoint));

        RequirePerm annotation = method.getAnnotation(RequirePerm.class);
        assertThat(annotation).as("%s 必须声明 @RequirePerm", endpoint).isNotNull();
        assertThat(annotation.value()).as("%s 的权限码", endpoint).containsExactly(endpoint.permission());

        HandlerMethod handler = new HandlerMethod(endpoint.controller(), method);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/view-probe");
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 9006L, "jti-staff"));

        PermissionInterceptor denied = new PermissionInterceptor((staffId, permission) -> false);
        assertThatThrownBy(() -> denied.preHandle(request, new MockHttpServletResponse(), handler))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        PermissionChecker granted = (staffId, permission) -> endpoint.permission().equals(permission);
        assertThat(new PermissionInterceptor(granted).preHandle(request, new MockHttpServletResponse(), handler))
                .as("%s 持有 %s 时应放行（Controller 正常返回 200）", endpoint, endpoint.permission())
                .isTrue();
    }
}
