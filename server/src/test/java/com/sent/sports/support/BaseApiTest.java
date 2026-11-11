package com.sent.sports.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc 接口契约测试基类（P4-01）。
 * <p>约定：
 * <ul>
 *   <li>具体测试类用 {@code @WebMvcTest(XxxController.class)} 指定被测控制器，并 {@code @MockBean} 其依赖。</li>
 *   <li>{@code addFilters = false} 关闭 Spring Security 默认过滤器链，使未带令牌的请求也能直达控制器；
 *       权限校验（@RequirePerm）与令牌解析（@CurrentUserArg）的拦截器在 Phase 5 才接入，
 *       故本阶段只验证「请求→控制器→用例→响应」的契约。</li>
 *   <li>提供 {@link #toJson(Object)} 便捷序列化请求体，子类直接用 {@link #mvc} 发请求。</li>
 * </ul>
 */
@AutoConfigureMockMvc(addFilters = false)
public abstract class BaseApiTest {

    @Autowired
    protected MockMvc mvc;

    protected final ObjectMapper json = new ObjectMapper();

    /** 将对象序列化为 JSON 字符串，用作请求体内容。 */
    protected String toJson(Object obj) throws JsonProcessingException {
        return json.writeValueAsString(obj);
    }
}
