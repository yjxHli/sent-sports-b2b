package com.sent.sports.finance.pdf;

/**
 * 发票税额拆分：订单侧金额是「含税」，票面需要「金额 / 税率 / 税额」三列。
 * <p>税率固定 13%（体育用品适用增值税税率）。为保证「金额合计 + 税额合计 = 价税合计」
 * 分毫不差，不含税金额向下取整，税额吸收余数 —— 反过来（税额取整）会让整票差 1 分。</p>
 */
public final class InvoiceTaxCalculator {

    /** 增值税税率 13% 的分子分母。 */
    private static final long NET_NUMERATOR = 100L;
    private static final long NET_DENOMINATOR = 113L;

    private InvoiceTaxCalculator() {
    }

    /**
     * 拆分结果。
     *
     * @param netCent     不含税金额（分）
     * @param taxCent     税额（分），恒等于 含税 − 不含税
     * @param unitNetCent 不含税单价（分），数量为 0 时为 0
     */
    public record TaxSplit(long netCent, long taxCent, long unitNetCent) {
    }

    /**
     * 按 13% 拆分一条含税明细。
     *
     * @param grossCent 含税金额（分），负数与 0 一律按 0 处理
     * @param qty       数量，仅用于反算不含税单价
     */
    public static TaxSplit split13(long grossCent, int qty) {
        long gross = Math.max(grossCent, 0L);
        long net = gross * NET_NUMERATOR / NET_DENOMINATOR;
        long tax = gross - net;
        long unitNet = qty > 0 ? net / qty : 0L;
        return new TaxSplit(net, tax, unitNet);
    }
}
