package com.sent.sports.finance.service.dto;

/**
 * 发票 PDF 出参：内容 + 建议下载文件名。
 * <p>内容以 {@code byte[]} 直接回写响应体，不落磁盘、不依赖静态资源目录 —— 之前
 * {@code invoice_url} 指向的 {@code /files/invoice/*.pdf} 从来不存在，点下载必 404。</p>
 */
public record InvoicePdfFile(byte[] content, String fileName) {
}
