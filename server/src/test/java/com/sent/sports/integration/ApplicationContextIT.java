package com.sent.sports.integration;

import com.sent.sports.SentSportsApplication;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全量 Spring 上下文加载测试（P5 前置阻塞验收）：用例 Bean、仓储端口、基础设施 Bean 必须全部装配成功。
 * <p>这是 P5-48~50 全链路集成测试的前置门槛——上下文起不来，链路测试无从谈起。
 */
@SpringBootTest(classes = SentSportsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class ApplicationContextIT {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("30 个用例类与关键端口均可从容器取得")
    void allBeansAreWired() {
        assertThat(context.getBean(CreateOrderUseCase.class)).isNotNull();
        assertThat(context.getBean(StockRepository.class)).isNotNull();
        assertThat(context.getBean(com.sent.sports.common.util.IdGen.class)).isNotNull();
        assertThat(context.getBean(com.sent.sports.common.event.DomainEventPublisher.class)).isNotNull();
        assertThat(context.getBean(com.sent.sports.common.idempotent.IdempotencyStore.class)).isNotNull();
        assertThat(context.getBean(com.sent.sports.common.ratelimit.RateLimiter.class)).isNotNull();
        assertThat(context.getBean(com.sent.sports.payment.gateway.PayGateway.class)).isNotNull();
        assertThat(context.getBean(com.sent.sports.common.storage.LocalFileStorage.class)).isNotNull();
    }

    @Test
    @DisplayName("用例类全部注册为 Spring Bean（@Service）")
    void allUseCasesAreBeans() {
        // 用例被 @UseCase 方法上的事务切面代理，getClass() 是 CGLIB 子类，须用 ClassUtils.getUserClass 还原
        long useCaseBeans = context.getBeansWithAnnotation(org.springframework.stereotype.Service.class)
                .values().stream()
                .map(bean -> org.springframework.util.ClassUtils.getUserClass(bean.getClass()))
                .filter(clazz -> clazz.getName().endsWith("UseCase"))
                .distinct()
                .count();
        assertThat(useCaseBeans).isGreaterThanOrEqualTo(30);
    }
}
