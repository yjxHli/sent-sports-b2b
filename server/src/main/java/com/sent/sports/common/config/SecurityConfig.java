package com.sent.sports.common.config;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Web 安全链（P5-34 收尾）：放行 {@code /ws/chat} 握手与业务 API 的 HTTP 层校验。
 * <p>本项目是自研 JWT 体系：{@code AuthFilter} 负责解析/续期令牌，{@code PermissionInterceptor}
 * 负责权限码，二者都读 {@code CurrentUser}。若沿用 Spring Security 默认链，
 * 所有请求（含 WebSocket 握手）会被 formLogin / basic / csrf 拦下返回 302/403，
 * 因此这里显式声明无状态、全放行的链——「放行 HTTP 层」不等于「免鉴权」，业务层仍逐请求校验。
 * <p>两处刻意保守：
 * <ul>
 *   <li>用 {@link AntPathRequestMatcher} 而非 {@code requestMatchers(String...)}（后者会转成
 *       MvcRequestMatcher，在 {@code webEnvironment=NONE} 的集成测试中因缺少
 *       {@code mvcHandlerMappingIntrospector} 而启动失败）；</li>
 *   <li>不启用 Security 的 CORS 配置器（同样依赖 MVC introspector），跨域统一由
 *       {@code WebMvcConfig#addCorsMappings} 处理。</li>
 * </ul>
 * 生产环境若要叠加网关 WAF / IP 白名单，应在本链上加 matcher，而不是恢复默认链。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    @Qualifier("authFilter") Filter authFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // AuthFilter 必须在 SecurityContextHolderFilter 之后执行：链首会建立空 Context，
                // 我们随后写入的 Authentication 才能在 Controller 内被 SecurityContextHolder 读到，
                // 并在请求结束时由 Spring Security 统一清理（无需自行 clearContext）。
                .addFilterAfter(authFilter, SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/actuator/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/ws/chat"),
                                new AntPathRequestMatcher("/ws/chat/**")).permitAll()
                        .anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
