package com.sent.sports.support;

import com.sent.sports.common.config.MybatisPlusConfig;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * 集成测试专用「持久层切片」应用（P5-23）。
 * <p>为什么不直接加载 {@code SentSportsApplication}：Phase 5 进行中的系统尚未完成全部装配
 * （用例类仍是纯 POJO、仓储适配器在 P5-19~P5-22 才落地），全量上下文此时无法启动。
 * 持久层集成测试只关心「迁移脚本 + Mapper + 真实 MySQL」，因此刻意只装配
 * 数据源 / Flyway / MyBatis-Plus / JdbcTemplate 四块，既快又能精准定位问题；
 * 全链路集成测试（P5-48~P5-50）会在装配完成后另用全量上下文。
 * <p>{@code @MapperScan} 显式限定「带 {@code @Mapper} 注解的接口」，避免把仓储端口等业务接口误注册成 Mapper。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackages = "com.sent.sports", annotationClass = Mapper.class)
@ComponentScan(basePackages = "com.sent.sports", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.sent\\.sports\\..*\\.repository\\..*RepositoryImpl"))
@Import(MybatisPlusConfig.class)
public class PersistenceTestApplication {
}
