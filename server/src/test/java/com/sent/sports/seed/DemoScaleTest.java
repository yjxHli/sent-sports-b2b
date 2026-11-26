package com.sent.sports.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DemoScale} 的配置解析与规模常量（纯单元测试，不起 Spring）。
 *
 * <p>守两件事：一是 {@code full} 档的数字不能漂（它决定演示环境的样子）；
 * 二是 {@code tiny} 必须真的小、且幂等阈值必须为 0 —— 否则每次重启都会再叠 50 单，
 * 几次之后 tiny 就退化成了全量。</p>
 */
class DemoScaleTest {

    @Test
    @DisplayName("full 档保持改造前的规模：150 个人 / 50 企业 / 2000 单 / 30 会话 / 阈值 100")
    void fullKeepsLegacyVolume() {
        DemoScale full = DemoScale.FULL;
        assertThat(full.personalCustomers()).isEqualTo(150);
        assertThat(full.enterpriseCustomers()).isEqualTo(50);
        assertThat(full.orderCount()).isEqualTo(2000);
        assertThat(full.chatSessions()).isEqualTo(30);
        assertThat(full.skipOrderThreshold()).isEqualTo(100);
        assertThat(full.isTiny()).isFalse();
    }

    @Test
    @DisplayName("tiny 档：12 个人 + 25 企业 / 115 单 / 阈值 0（重启不再叠加）")
    void tinyIsSmallAndIdempotent() {
        DemoScale tiny = DemoScale.TINY;
        assertThat(tiny.personalCustomers())
                .as("个人客户要垫出「第 2 页」，12 是首页 size=10 之后还剩 2 条的最小值")
                .isEqualTo(12);
        assertThat(tiny.enterpriseCustomers())
                .as("企业列表断言 >20 条（分页 + 合同/应收也按企业铺），且 seq%%7 分档要凑出「已过期」")
                .isEqualTo(25);
        assertThat(tiny.orderCount())
                .as("订单数由 tinyOrderPlan() 实际产出，必须等于计划条数，否则 plan 与常量会悄悄漂移")
                .isEqualTo(115);
        assertThat(tiny.chatSessions()).isPositive();
        assertThat(tiny.skipOrderThreshold())
                .as("tiny 必须订单 >0 就跳过，否则每次重启叠加 115 单")
                .isZero();
        assertThat(tiny.isTiny()).isTrue();
    }

    @Test
    @DisplayName("配置解析：大小写/空白容忍，未知值一律回落到 full")
    void parsesConfigLenientlyAndFallsBackToFull() {
        assertThat(DemoScale.of("tiny")).isEqualTo(DemoScale.TINY);
        assertThat(DemoScale.of("TINY")).isEqualTo(DemoScale.TINY);
        assertThat(DemoScale.of("  tiny  ")).isEqualTo(DemoScale.TINY);
        assertThat(DemoScale.of("full")).isEqualTo(DemoScale.FULL);
        assertThat(DemoScale.of("")).isEqualTo(DemoScale.FULL);
        assertThat(DemoScale.of(null)).isEqualTo(DemoScale.FULL);
        assertThat(DemoScale.of("small")).as("拼错不静默变小，按保守的全量处理").isEqualTo(DemoScale.FULL);
    }
}
