package com.sent.sports.common.config;

import com.sent.sports.common.storage.LocalFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * 公共基础设施装配（P5）：事务模板、本地文件存储。
 * <p>{@code UseCaseAspect} 需要 {@link TransactionTemplate} 包裹用例事务，Spring Boot 只自动提供
 * {@link PlatformTransactionManager}，模板必须显式声明，否则全量上下文起不来。
 */
@Configuration
public class CommonInfraConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public LocalFileStorage localFileStorage(@Value("${app.upload.base-dir:/data/upload}") String baseDir,
                                            Clock clock) {
        return new LocalFileStorage(baseDir, clock);
    }
}
