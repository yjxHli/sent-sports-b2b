package com.sent.sports.auth.service.dto;

import com.sent.sports.auth.token.TokenService;

/**
 * 登录命令（中立入参）：用户名 + 密码 + 登录端 + 设备标识。
 * <p>{@code aud} / {@code deviceId} 供 P5-25 刷新令牌按设备隔离使用；
 * 两参构造器保留给既有调用方（默认管理端 + 缺省设备）。
 */
public record LoginCmd(String username, String password, String aud, String deviceId) {

    public static final String DEFAULT_DEVICE = "default";

    public LoginCmd(String username, String password) {
        this(username, password, TokenService.AUD_ADMIN, DEFAULT_DEVICE);
    }

    /** 归一化：空 aud / 空设备一律回落到默认值，避免存储端出现 null 键。 */
    public String normalizedAud() {
        return (aud == null || aud.isBlank()) ? TokenService.AUD_ADMIN : aud;
    }

    public String normalizedDeviceId() {
        return (deviceId == null || deviceId.isBlank()) ? DEFAULT_DEVICE : deviceId;
    }
}
