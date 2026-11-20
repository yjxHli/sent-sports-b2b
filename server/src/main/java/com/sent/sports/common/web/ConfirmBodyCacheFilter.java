package com.sent.sports.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 确认令牌请求体缓存（R10 配套）：把带 {@code X-Confirm-Token} 的 JSON 请求体预读进内存，
 * 让 {@code ConfirmTokenInterceptor} 能在 {@code preHandle}（参数绑定之前）算出目标 ID，
 * 同时保证随后的 {@code @RequestBody} 仍能读到完整正文。
 *
 * <p>只碰「有令牌 + JSON」的请求：普通业务请求零拷贝；multipart 上传（文件表单）完全不经过本类，
 * 不会被误读到内存里。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ConfirmBodyCacheFilter extends OncePerRequestFilter {

    /** 预读到的请求体字节，供拦截器与正文解析共用。 */
    public static final String BODY_ATTR = "com.sent.sports.confirmBody";

    private static final String HEADER = "X-Confirm-Token";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!shouldCache(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        request.setAttribute(BODY_ATTR, body);
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static boolean shouldCache(HttpServletRequest request) {
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            return false;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String contentType = request.getContentType();
        return contentType != null
                && (contentType.contains("application/json") || contentType.contains("+json"));
    }

    /** 可重复读取的请求包装：每次 {@code getInputStream()} 返回一份新的字节流。 */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    // 同步读取场景（Spring MVC 参数绑定）不注册异步监听
                }

                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buf, int offset, int length) {
                    return source.read(buf, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = super.getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(super.getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
