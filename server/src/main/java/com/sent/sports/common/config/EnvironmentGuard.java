package com.sent.sports.common.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境守卫（R-03）：启动时必须校验 {@code app.env} 与数据源库名后缀一致，
 * 不一致直接启动失败并打印明确原因，避免把测试数据写进生产库。
 *
 * 例：env=prod 时数据源 url 的库名必须以 {@code _prod} 结尾。
 */
@Component
public class EnvironmentGuard {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentGuard.class);
    private static final Pattern DB_NAME = Pattern.compile("/([a-zA-Z0-9_]+)\\?");

    private final Environment environment;
    private final String appEnv;
    private final String datasourceUrl;

    public EnvironmentGuard(Environment environment,
                           @Value("${app.env:dev}") String appEnv,
                           @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.environment = environment;
        this.appEnv = appEnv;
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void validate() {
        // 测试环境若由 Testcontainers 动态注入，url 可能为空或随机，跳过强校验
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod")) && !"prod".equals(appEnv)) {
            Matcher m = DB_NAME.matcher(datasourceUrl);
            if (m.find()) {
                String dbName = m.group(1);
                if (!dbName.endsWith("_" + appEnv)) {
                    throw new IllegalStateException(
                            String.format("环境校验失败：app.env=%s 但数据源库名=%s，后缀应为 _%s", appEnv, dbName, appEnv));
                }
            }
            log.info("环境守卫通过：app.env={}, datasource={}", appEnv, mask(datasourceUrl));
            return;
        }
        // 生产环境严格校验
        Matcher m = DB_NAME.matcher(datasourceUrl);
        if (m.find()) {
            String dbName = m.group(1);
            if (!dbName.endsWith("_prod")) {
                throw new IllegalStateException(
                        String.format("生产环境校验失败：数据源库名=%s 必须以 _prod 结尾", dbName));
            }
        }
        log.info("环境守卫通过（prod）：datasource={}", mask(datasourceUrl));
    }

    private static String mask(String url) {
        if (url == null) {
            return "<empty>";
        }
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
