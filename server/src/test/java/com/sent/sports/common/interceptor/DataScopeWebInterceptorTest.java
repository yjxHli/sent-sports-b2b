package com.sent.sports.common.interceptor;

import com.sent.sports.common.mybatis.DataScopeContext;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.DataScopeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 数据范围 Web 入口单测（P5-28）：admin 绑定上下文、user 跳过、请求结束清理。 */
class DataScopeWebInterceptorTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void adminRequestBindsScope() {
        DataScopeResolver resolver = staffId -> Optional.of(DataScopeContext.Scope.self(staffId));
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 7L, "jti-1"));

        assertTrue(new DataScopeWebInterceptor(resolver).preHandle(request, response, new Object()));
        assertEquals(7L, DataScopeContext.current().orElseThrow().staffId());
    }

    @Test
    void userRequestIsIgnored() {
        DataScopeResolver resolver = staffId -> Optional.of(DataScopeContext.Scope.self(staffId));
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 7L, "jti-1"));

        new DataScopeWebInterceptor(resolver).preHandle(request, response, new Object());

        assertTrue(DataScopeContext.current().isEmpty());
    }

    @Test
    void anonymousRequestIsIgnored() {
        DataScopeResolver resolver = staffId -> Optional.of(DataScopeContext.Scope.self(staffId));

        new DataScopeWebInterceptor(resolver).preHandle(request, response, new Object());

        assertTrue(DataScopeContext.current().isEmpty());
    }

    @Test
    void emptyResolverResultLeavesContextClean() {
        DataScopeResolver resolver = staffId -> Optional.empty();
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 7L, "jti-1"));

        new DataScopeWebInterceptor(resolver).preHandle(request, response, new Object());

        assertTrue(DataScopeContext.current().isEmpty());
    }

    @Test
    void afterCompletionAlwaysClearsThreadLocal() {
        DataScopeResolver resolver = staffId -> Optional.of(DataScopeContext.Scope.all(staffId));
        request.setAttribute(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 7L, "jti-1"));
        DataScopeWebInterceptor interceptor = new DataScopeWebInterceptor(resolver);
        interceptor.preHandle(request, response, new Object());
        assertTrue(DataScopeContext.current().isPresent());

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        assertFalse(DataScopeContext.current().isPresent());
    }
}
