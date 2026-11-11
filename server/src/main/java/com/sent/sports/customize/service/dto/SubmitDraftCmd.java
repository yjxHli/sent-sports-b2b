package com.sent.sports.customize.service.dto;

/**
 * 提交图稿命令（中立入参）：定制单号 + 单个附件信息。spec §6.5.3——每次提交生成新版本。
 */
public record SubmitDraftCmd(String customOrderId, String fileId, String fileName, long fileSize) {
}
