package com.sent.sports.system.mapper.po;

import java.time.LocalDateTime;

/**
 * 违禁词行（{@code sys_sensitive_word}）。
 *
 * @param id        主键
 * @param word      违禁词原文
 * @param category  分类：PROFANITY / AD / ILLEGAL / OTHER
 * @param enabled   1 启用、0 停用（TINYINT 映射成 Integer，便于前端直接判真假）
 * @param remark    备注
 * @param createdAt 录入时间
 * @param updatedAt 最后修改时间
 */
public record SensitiveWordRow(Long id, String word, String category, Integer enabled,
                               String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
