package com.sent.sports.common.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.web.ConfirmBodyCacheFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 危险操作确认令牌拦截器（R10）：把原先 14 个 Controller 里逐段复制的
 * 「取头 → 算目标 → consume → 40302」收敛成一份实现。
 *
 * <p>为什么用拦截器而不是 AOP：校验发生在参数绑定之前，非法请求根本进不了业务方法；
 * 而且 Header 语义本就属于 Web 层，拦截器比切面更贴近契约。</p>
 *
 * <p>目标串口径与换令牌端完全一致（{@code BatchIds} 归一化），归一到位后写回
 * {@link #TARGET_ATTR}，业务方法如需审计可直接取用。</p>
 */
public class ConfirmTokenInterceptor implements HandlerInterceptor {

    /** 校验通过后写回的目标串，供业务侧审计/日志复用。 */
    public static final String TARGET_ATTR = "com.sent.sports.confirmTarget";

    private static final String HEADER = "X-Confirm-Token";

    private final ConfirmTokenService confirmTokenService;
    private final ObjectMapper objectMapper;

    public ConfirmTokenInterceptor(ConfirmTokenService confirmTokenService, ObjectMapper objectMapper) {
        this.confirmTokenService = confirmTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Confirm confirm = handlerMethod.getMethodAnnotation(Confirm.class);
        if (confirm == null) {
            return true;
        }
        String token = request.getHeader(HEADER);
        // 先判空再算目标：缺令牌必须稳定返回 40302，不能被请求体缺字段的 40001 抢先
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.CONFIRM_TOKEN_INVALID);
        }
        String targetId = resolveTarget(request, confirm);
        request.setAttribute(TARGET_ATTR, targetId);
        if (!confirmTokenService.consume(confirm.action().name(), targetId, token)) {
            throw new BizException(ErrorCode.CONFIRM_TOKEN_INVALID);
        }
        return true;
    }

    /** 按注解声明的来源算目标串；算不出来直接 40001，不降级放行。 */
    private String resolveTarget(HttpServletRequest request, Confirm confirm) {
        return switch (confirm.target()) {
            case PATH_VARIABLE -> pathVariable(request, confirm.field());
            case BATCH_IDS -> BatchIds.targetId(bodyArray(request, confirm.field()));
            case BODY_FIELD -> {
                JsonNode node = bodyNode(request).path(confirm.field());
                if (node.isArray()) {
                    yield BatchIds.targetId(toList(node));
                }
                String value = node.asText("").trim();
                if (value.isEmpty()) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "缺少确认令牌绑定的目标标识：" + confirm.field());
                }
                yield value;
            }
        };
    }

    private String pathVariable(HttpServletRequest request, String name) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Object value = vars instanceof Map<?, ?> map ? map.get(name) : null;
        String target = value == null ? "" : value.toString().trim();
        if (target.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少确认令牌绑定的目标标识：" + name);
        }
        return target;
    }

    /** 请求体数组字段：不在数组或为空时由 {@link BatchIds} 抛「请先选择要删除的记录」。 */
    private List<String> bodyArray(HttpServletRequest request, String field) {
        JsonNode node = bodyNode(request).path(field);
        return node.isArray() ? toList(node) : List.of();
    }

    private JsonNode bodyNode(HttpServletRequest request) {
        Object cached = request.getAttribute(ConfirmBodyCacheFilter.BODY_ATTR);
        byte[] body = cached instanceof byte[] bytes ? bytes : new byte[0];
        if (body.length == 0) {
            // 有正文却没被缓存：说明过滤器没生效，宁可显式报错也不要静默按空集合放行
            if (request.getContentLengthLong() > 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请求体未被缓存，无法校验确认令牌");
            }
            // 真正的空正文：交给 BatchIds 给出「请先选择要删除的记录」
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (java.io.IOException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体格式错误，无法校验确认令牌");
        }
    }

    private static List<String> toList(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(item -> list.add(item.asText()));
        return list;
    }
}
