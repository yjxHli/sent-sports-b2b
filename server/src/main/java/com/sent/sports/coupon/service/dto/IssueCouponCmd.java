package com.sent.sports.coupon.service.dto;

import java.util.List;

/**
 * @param templateId  券模板 ID（决定面额与有效期）
 * @param customerIds 指定客户；为空表示全员发放
 * @param thresholdCent 发放时覆盖的门槛（分）；null 表示沿用模板默认，0 表示真正的无门槛
 * @param reason      发放原因，售后补券时写入站内信
 * @param scene       ACTIVITY 平台活动 / AFTER_SALE 售后补偿
 */
public record IssueCouponCmd(long templateId, List<Long> customerIds, Long thresholdCent,
                             String reason, String scene) {
}
