package com.sent.sports.common.web;

/**
 * 当前登录人（P5-26）：由 {@code AuthFilter} 解析 Access Token 后注入 request 属性，
 * 供权限拦截器与控制器使用。{@code aud} 区分 user / admin 两端。
 */
public record CurrentUser(String aud, long userId, String jti) {

    /** request 属性键。 */
    public static final String ATTR = "com.sent.sports.currentUser";

    public static final String AUD_USER = "user";
    public static final String AUD_ADMIN = "admin";

    public boolean isAdmin() {
        return AUD_ADMIN.equals(aud);
    }
}
