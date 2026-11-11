package com.sent.sports.common.anno;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 用例事务切面（P3-03）：对所有 {@link UseCase} 标注的方法包裹事务，异常回滚。
 * 依赖 Spring {@link TransactionTemplate}；单元测试不加载 Spring 上下文，故不影响单测。
 */
@Aspect
@Component
public class UseCaseAspect {

    private final TransactionTemplate transactionTemplate;

    public UseCaseAspect(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Around("@annotation(com.sent.sports.common.anno.UseCase)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        return transactionTemplate.execute(status -> {
            try {
                return pjp.proceed();
            } catch (RuntimeException e) {
                status.setRollbackOnly();
                throw e;
            } catch (Throwable t) {
                status.setRollbackOnly();
                throw new RuntimeException(t);
            }
        });
    }
}
