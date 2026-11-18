package com.sent.sports.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 客服满意度评价的入参 / 出参（独立于 AdminChatDtos，避免把一个文件撑到几百行）。
 */
public final class SatisfactionDtos {

    private SatisfactionDtos() {
    }

    /** 客户打分：满意 / 一般 / 不满意。 */
    public record RateCmd(@NotBlank(message = "评价档位不能为空") String score) {
    }

    public record RateResult(long sessionId, String score, String messageId) {
    }

    /**
     * 当日满意率（只统计当前客服、当天已提交的评价）。
     * 无评价时 ratePercent 为 -1，前端显示 `--` 而不是 0%。
     */
    public record TodayRateVO(int ratedCount, int satisfiedCount, int ratePercent) {
        public static TodayRateVO empty() {
            return new TodayRateVO(0, 0, -1);
        }
    }
}
