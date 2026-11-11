package com.sent.sports.common.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏注解（P4-03）：标注在响应 VO 的字段上，序列化时按当前用户角色决定是否置空。
 * <p>例如 {@code @Sensitive(hideFor = {"WAREHOUSE"})} 表示当当前角色命中 WAREHOUSE 时，
 * Jackson 序列化该字段为 {@code null}，避免低权限角色看到敏感字段（plan §11 权限条）。</p>
 * 实际置空逻辑由 SensitiveSerializer / 序列化过滤器（Phase 5）读取当前角色实现。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
    /** 当当前角色命中以下任一角色时，字段置为 null */
    String[] hideFor();
}
