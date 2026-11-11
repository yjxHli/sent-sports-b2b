package com.sent.sports.common.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用例注解（P3-03）：标注在应用用例的执行方法上，由 {@link UseCaseAspect} 织入事务边界，异常回滚。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    String value() default "";
}
