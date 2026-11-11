package com.sent.sports.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.auth.token.TokenVersionLookup;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 鉴权过滤器（P5-26）。
 * <p>解析 {@code Authorization: Bearer <AccessToken>} → 校验令牌与 token_version → 注入 {@link CurrentUser}；
 * 剩余有效期小于阈值时下发 {@code X-New-Token}（滑动续期，复用同 jti）；过期 → {@code 40102}，
 * 非法/签名错 → {@code 40101}（供前端分流：40102 走 refresh，40101 跳登录）。白名单路径不解析令牌。
 * 无令牌一律按匿名放行，受保护端点的拒绝由权限拦截器（P5-27）负责。</p>
 * <p>令牌相关异常在校验阶段即处理完毕，{@code chain.doFilter} 在异常处理之外执行，
 * 避免下游业务异常被误判为 401。</p>
 */
public class AuthFilter implements Filter {

    private static final String BEARER = "Bearer ";

    private final TokenService tokenService;
    private final TokenVersionLookup versionLookup;
    private final ObjectMapper objectMapper;
    private final Set<String> whitelist;

    public AuthFilter(TokenService tokenService, TokenVersionLookup versionLookup,
                      ObjectMapper objectMapper, Set<String> whitelist) {
        this.tokenService = tokenService;
        this.versionLookup = versionLookup;
        this.objectMapper = objectMapper;
        this.whitelist = whitelist;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (isWhitelisted(req.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER.length()).trim();
        TokenService.Verified verified;
        try {
            verified = tokenService.verify(token);
        } catch (BizException e) {
            writeError(res, e.getErrorCode());
            return;
        }

        if (verified.tokenVersion() != versionLookup.currentVersion(verified.aud(), verified.userId())) {
            writeError(res, ErrorCode.TOKEN_EXPIRED);
            return;
        }

        CurrentUser current = new CurrentUser(verified.aud(), verified.userId(), verified.jti());
        // 两套读取入口并存：request 属性供既有 Controller / 拦截器使用；
        // SecurityContext 供 SecurityContextHolder.getContext().getAuthentication() 的常规写法使用，
        // principal 直接用 JWT 的 sub（userId 字符串），Controller 用
        // SecurityContextHolder.getContext().getAuthentication().getName() 即可取到；
        // details 里保留完整 CurrentUser（aud / jti），request 属性继续兼容既有读取方式。
        req.setAttribute(CurrentUser.ATTR, current);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                Long.toString(verified.userId()), null, List.of());
        authentication.setDetails(current);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        if (tokenService.shouldRenew(verified.expireAtMillis())) {
            TokenService.Issued renewed = tokenService.issueAccess(new TokenService.Claims(
                    verified.aud(), verified.userId(), verified.tokenVersion(), verified.jti()));
            res.setHeader("X-New-Token", renewed.accessToken());
        }

        chain.doFilter(request, response);
    }

    private boolean isWhitelisted(String path) {
        for (String allowed : whitelist) {
            if (path.equals(allowed) || path.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.getHttpStatus());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(R.fail(code.getCode(), code.getDefaultMessage())));
    }
}
