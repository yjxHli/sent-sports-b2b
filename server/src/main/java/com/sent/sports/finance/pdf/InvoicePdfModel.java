package com.sent.sports.finance.pdf;

import java.util.List;

/**
 * 发票票面模型（增值税电子普通发票）：渲染器只认这个模型，不碰数据库。
 *
 * @param title  票头，如「江苏增值税电子普通发票」
 * @param meta   发票代码 / 号码 / 开票日期 / 校验码
 * @param buyer  购买方
 * @param seller 销售方
 * @param remark 备注（订单号等）
 * @param lines  明细行（超出票面行数时由组装层折叠）
 * @param totals 金额合计 / 税额合计 / 价税合计
 */
public record InvoicePdfModel(
        String title,
        Meta meta,
        Party buyer,
        Party seller,
        String remark,
        List<InvoicePdfLine> lines,
        Totals totals,
        String payee,
        String reviewer,
        String drawer
) {

    /** 发票标识信息。 */
    public record Meta(String invoiceCode, String invoiceNo, String issuedDate, String checkCode) {
    }

    /** 开票双方（购买方 / 销售方）：名称、纳税人识别号、地址电话、开户行及账号。 */
    public record Party(String name, String taxNo, String addressPhone, String bankAccount) {
    }

    /** 票面三档金额（均以分为单位）。 */
    public record Totals(long amountCent, long taxCent, long grossCent) {
    }

    /** 明细行数；防御性返回空列表，避免渲染器到处判空。 */
    @Override
    public List<InvoicePdfLine> lines() {
        return lines == null ? List.of() : lines;
    }
}
