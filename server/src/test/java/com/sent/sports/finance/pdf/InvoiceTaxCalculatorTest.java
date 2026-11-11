package com.sent.sports.finance.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发票税额拆分：订单侧金额是「含税」，票面要求「金额 / 税率 / 税额」三列且
 * 金额合计 + 税额合计 必须精确等于价税合计（差 1 分钱发票就作废）。
 */
class InvoiceTaxCalculatorTest {

    @Test
    @DisplayName("单行 13%：不含税 + 税额 = 含税，且各自按分取整")
    void splitsThirteenPercent() {
        InvoiceTaxCalculator.TaxSplit split = InvoiceTaxCalculator.split13(11_300L, 1);
        assertEquals(10_000L, split.netCent());
        assertEquals(1_300L, split.taxCent());
        assertEquals(11_300L, split.netCent() + split.taxCent());
    }

    @Test
    @DisplayName("除不尽的含税金额：税额吸收余数，总额分毫不差")
    void remainderAbsorbedByTax() {
        InvoiceTaxCalculator.TaxSplit split = InvoiceTaxCalculator.split13(20_000L, 1);
        assertEquals(17_699L, split.netCent(), "20000 / 1.13 = 17699.11 → 向下取整");
        assertEquals(2_301L, split.taxCent());
        assertEquals(20_000L, split.netCent() + split.taxCent());
    }

    @Test
    @DisplayName("多行明细：逐行拆分后合计与订单含税总额一致")
    void totalsMatchOrderAmount() {
        List<Long> gross = List.of(20_000L, 12_345L, 99L, 1_000_000L);
        long totalNet = 0;
        long totalTax = 0;
        for (Long g : gross) {
            InvoiceTaxCalculator.TaxSplit split = InvoiceTaxCalculator.split13(g, 1);
            totalNet += split.netCent();
            totalTax += split.taxCent();
            assertTrue(split.netCent() >= 0 && split.taxCent() >= 0);
        }
        assertEquals(gross.stream().mapToLong(Long::longValue).sum(), totalNet + totalTax);
    }

    @Test
    @DisplayName("数量 > 1 时给出不含税单价，单价 × 数量与不含税金额对不上时以金额为准")
    void unitPriceIsNet() {
        InvoiceTaxCalculator.TaxSplit split = InvoiceTaxCalculator.split13(113_000L, 10);
        assertEquals(100_000L, split.netCent());
        assertEquals(10_000L, split.unitNetCent());
    }

    @Test
    @DisplayName("0 元明细不除零、不抛异常")
    void zeroAmountIsSafe() {
        InvoiceTaxCalculator.TaxSplit split = InvoiceTaxCalculator.split13(0L, 0);
        assertEquals(0L, split.netCent());
        assertEquals(0L, split.taxCent());
        assertEquals(0L, split.unitNetCent());
    }
}
