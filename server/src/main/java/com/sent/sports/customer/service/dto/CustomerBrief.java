package com.sent.sports.customer.service.dto;

/**
 * 客户展示信息（跨模块出参）：客服工作台左侧会话列表要显示「进线用户是谁」。
 *
 * <p>只暴露展示所需的最小字段，不带手机号、密码哈希等敏感信息。</p>
 *
 * @param customerId   客户 ID
 * @param nickname     昵称；未设置时为 null（调用方兜底成「客户 ID」）
 * @param avatarFileId 头像 fileId（V32），无头像为 null
 */
public record CustomerBrief(long customerId, String nickname, String avatarFileId) {
}
