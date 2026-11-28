package com.sent.sports.finance.service.dto;

import java.time.LocalDate;

/**
 * 待提醒合同（finance → job 的中立 DTO）。
 *
 * @param currentLevel 已提醒档位（0/1/2/3）
 * @param remindLevel  本次应提醒档位（1=30 天、2=7 天、3=到期当天）
 */
public record ContractReminder(long contractId, long customerId, String contractNo,
                               long amountCent, LocalDate expireDate,
                               int currentLevel, int remindLevel) {
}
