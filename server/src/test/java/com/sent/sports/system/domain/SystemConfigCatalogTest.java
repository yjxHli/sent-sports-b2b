package com.sent.sports.system.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置字典（V62 配套，TDD 红）：把 {@code sys_config} 的极客键名翻译成运营读得懂的配置项。
 *
 * <p>这是「去极客化」的唯一真源：中文名 / 说明 / 单位 / 取值类型只有一份，
 * 后端接口与前端表格都从这里取，不允许两边各写一份中文名（对不上时运营会以为改错了配置）。</p>
 */
class SystemConfigCatalogTest {

    @Test
    @DisplayName("已知键能查到中文名、说明、单位与分组")
    void knownKeyExposesReadableMetadata() {
        SystemConfigCatalog timeout = SystemConfigCatalog.of("order.pay.timeout.minutes").orElseThrow();

        assertEquals("订单支付超时时间", timeout.displayName());
        assertEquals("分钟", timeout.unit());
        assertEquals("订单与交易", timeout.groupName());
        assertTrue(timeout.description().contains("自动取消"),
                "说明必须讲清「改这个值会发生什么」，而不是复述键名");
    }

    @Test
    @DisplayName("未知键返回空：库里可能有人手工加过键，不能因此 500")
    void unknownKeyIsAbsent() {
        assertEquals(Optional.empty(), SystemConfigCatalog.of("some.hand.added.key"));
        assertEquals(Optional.empty(), SystemConfigCatalog.of(null));
        assertEquals(Optional.empty(), SystemConfigCatalog.of("   "));
    }

    @Test
    @DisplayName("数字类只收非负整数，空值与字母都要拒绝")
    void numberNormalization() {
        SystemConfigCatalog timeout = SystemConfigCatalog.of("order.pay.timeout.minutes").orElseThrow();

        assertEquals("45", timeout.normalize(" 45 "));
        for (String bad : new String[]{null, "", "  ", "abc", "3.5", "-1", "30分钟"}) {
            BizException error = assertThrows(BizException.class, () -> timeout.normalize(bad),
                    "「" + bad + "」不是合法的分钟数");
            assertEquals(ErrorCode.PARAM_INVALID, error.getErrorCode());
        }
        assertTrue(assertThrows(BizException.class, () -> timeout.normalize("abc"))
                .resolvedMessage().contains("订单支付超时时间"),
                "报错要带上中文名，运营才知道是哪一项填错了");
    }

    @Test
    @DisplayName("税率只收 0~100：填 130 会让发票金额变成 2.3 倍")
    void percentHasUpperBound() {
        SystemConfigCatalog tax = SystemConfigCatalog.of("finance.invoice.tax.rate").orElseThrow();

        assertEquals("13", tax.normalize("13"));
        assertEquals("0", tax.normalize("0"));
        assertEquals("100", tax.normalize("100"));
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> tax.normalize("101")).getErrorCode());
    }

    @Test
    @DisplayName("展示值：数字带单位、金额按元千分位、布尔翻成开/关")
    void displayValueIsBusinessReadable() {
        assertEquals("30 分钟", SystemConfigCatalog.of("order.pay.timeout.minutes").orElseThrow().display("30"));
        assertEquals("7 天", SystemConfigCatalog.of("order.auto.confirm.days").orElseThrow().display("7"));
        assertEquals("13 %", SystemConfigCatalog.of("finance.invoice.tax.rate").orElseThrow().display("13"));
        assertEquals("¥50,000.00",
                SystemConfigCatalog.of("finance.credit.default.limit.cent").orElseThrow().display("5000000"));
        // 空值不显示成 "null 分钟"，统一给占位符，前端不必再判空
        assertEquals("未设置", SystemConfigCatalog.of("stock.warning.threshold").orElseThrow().display(null));
    }

    @Test
    @DisplayName("字典覆盖 V22 灌入的全部业务参数：漏一个，那个键就只能显示成英文键名")
    void catalogCoversSeededKeys() {
        String[] seeded = {
                "order.pay.timeout.minutes", "order.auto.confirm.days", "order.close.after.days",
                "stock.warning.threshold", "finance.invoice.tax.rate",
                "finance.credit.default.limit.cent", "finance.credit.default.term.days",
                "member.point.exchange.ratio", "member.level.upgrade.growth",
        };
        for (String key : seeded) {
            assertFalse(SystemConfigCatalog.of(key).isEmpty(), "字典缺少 " + key + " 的中文名");
        }
    }
}
