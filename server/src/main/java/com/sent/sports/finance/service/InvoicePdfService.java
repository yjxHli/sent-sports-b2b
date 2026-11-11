package com.sent.sports.finance.service;

import com.sent.sports.finance.service.dto.InvoicePdfFile;

/**
 * 发票 PDF 出票（增值税电子普通发票版式）。
 * <p>发票管理页的「预览 / 下载」直接走这里，票面由 {@code InvoicePdfRenderer} 现场生成，
 * 同一张发票多次生成内容一致（发票代码/校验码由主键确定性推导）。</p>
 */
public interface InvoicePdfService {

    /**
     * 生成指定发票的 PDF。
     *
     * @param invoiceId 发票申请主键
     * @return PDF 字节与该发票的建议文件名
     */
    InvoicePdfFile render(long invoiceId);
}
