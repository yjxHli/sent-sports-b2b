package com.sent.sports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 森特体育用品管理系统 · 启动类。
 * 模块化单体：按业务域分包（product/order/stock/...），模块间只通过 Service 接口与领域事件交互。
 */
@SpringBootApplication
public class SentSportsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentSportsApplication.class, args);
    }
}
