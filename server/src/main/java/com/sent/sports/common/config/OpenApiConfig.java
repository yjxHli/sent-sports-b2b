package com.sent.sports.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置：接口文档元信息与 Bearer Token 安全方案。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("森特体育用品管理系统 API")
                        .description("模块化单体后端接口文档")
                        .version("v1.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
