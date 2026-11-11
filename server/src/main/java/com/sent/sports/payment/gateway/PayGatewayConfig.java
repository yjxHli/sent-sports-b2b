package com.sent.sports.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 支付通道装配：本期使用可复现的模拟网关，接真实通道时替换本 Bean 的实现类。 */
@Configuration
public class PayGatewayConfig {

    @Bean
    public PayGateway payGateway(@Value("${app.pay.mock-secret}") String secret,
                                 Clock clock) {
        return new MockPayGateway(secret, clock);
    }
}
