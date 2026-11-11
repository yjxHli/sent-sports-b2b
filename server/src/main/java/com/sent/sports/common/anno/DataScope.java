package com.sent.sports.common.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围注解（P5-28 / plan §7.9）：标注在 <b>Mapper 方法</b> 上，声明该查询需要按角色数据范围过滤。
 * <p>列表与导出必须走同一个 Mapper 方法（或都标注本注解），避免「列表看不到但导出能拿到」。
 * 实际 SQL 改写由 {@code common.mybatis.DataScopeInterceptor} 在 StatementHandler.prepare 阶段完成。
 * <p>{@code alias} / {@code column} 均为代码内常量（不接受外部输入），拦截器会做标识符白名单校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 表别名，如 "o"；为空则直接用列名。 */
    String alias() default "";

    /** 归属员工列，默认 owner_staff_id。 */
    String column() default "owner_staff_id";
}
