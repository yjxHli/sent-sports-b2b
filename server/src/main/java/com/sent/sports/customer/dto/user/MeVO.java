package com.sent.sports.customer.dto.user;

/**
 * 用户端个人资料视图（收口阶段）：{@code /api/me} 响应。
 * <p>仅透出必要的基本信息、会员等级与积分；密码哈希等敏感字段绝不出现在此。</p>
 *
 * @param customerId    客户主键（即登录令牌中的 userId）
 * @param customerNo    客户编号
 * @param nickname      昵称
 * @param phone         手机号
 * @param email         邮箱
 * @param type          客户类型（PERSON / ENTERPRISE）
 * @param levelCode     会员等级代码
 * @param growthValue   成长值
 * @param pointsBalance 可用积分
 * @param status        账户状态（ACTIVE / DISABLED ...）
 */
public record MeVO(long customerId, String customerNo, String nickname, String phone, String email,
                   String type, String levelCode, long growthValue, long pointsBalance, String status) {
}
