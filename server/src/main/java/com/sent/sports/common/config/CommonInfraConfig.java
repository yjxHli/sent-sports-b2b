package com.sent.sports.common.config;

import com.sent.sports.common.storage.LocalFileStorage;
import com.sent.sports.common.util.SensitiveWordFilter;
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

    /**
     * 敏感词过滤器单例：启动时一次性把 classpath 词库编译成前缀树。
     * <p>词库缺失直接抛异常让应用起不来——「风控静默失效」比启动失败危险得多。</p>
     */
    @Bean
    public SensitiveWordFilter sensitiveWordFilter(
            @Value("${app.sensitive.words-file:sensitive_words.txt}") String wordsFile) {
        return SensitiveWordFilter.fromClasspath(wordsFile);
    }
}
