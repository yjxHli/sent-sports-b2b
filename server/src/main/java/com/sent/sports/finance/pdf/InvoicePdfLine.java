package com.sent.sports.finance.pdf;

/**
 * 发票明细行（票面「货物或应税劳务、服务名称」到「税额」一栏）。
 *
 * @param name          商品名称
 * @param spec          规格型号，缺失时为「无」
 * @param unit          单位
 * @param qty           数量
 * @param unitNetCent   不含税单价（分）
 * @param amountCent    不含税金额（分）
 * @param taxRate       税率文本，如「13%」
 * @param taxCent       税额（分）
 */
public record InvoicePdfLine(
        String name,
        String spec,
        String unit,
        int qty,
        long unitNetCent,
        long amountCent,
        String taxRate,
        long taxCent
) {
}
