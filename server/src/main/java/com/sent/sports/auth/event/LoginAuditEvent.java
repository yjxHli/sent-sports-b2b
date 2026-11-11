package com.sent.sports.auth.event;

/**
 * 登录审计事件（auth 模块，P3-61）：登录成功/失败的留痕，由审计模块监听持久化。
 */
public record LoginAuditEvent(long userId, boolean success, long atMillis) {
}
