package com.sent.sports.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 用例测试基类（P3-01）：继承单测基类（Mockito + 固定时钟），
 * 额外提供无副作用的固定事务模板，便于用例层在测试中验证事务边界而不依赖 Spring 上下文。
 */
public abstract class BaseUseCaseTest extends BaseUnitTest {

    /** 固定事务模板：不真正开启数据库事务，仅透传回调，保证用例方法可被事务包裹。 */
    protected TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new NoopPlatformTransactionManager());
    }

    /** 透传型事务管理器：仅返回占位 TransactionStatus，commit/rollback 为空操作。 */
    private static final class NoopPlatformTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // 空操作：单元测试不提交真实事务
        }

        @Override
        public void rollback(TransactionStatus status) {
            // 空操作：单元测试不回滚真实事务
        }
    }
}
