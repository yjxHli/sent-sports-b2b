package com.sent.sports.customer.service.dto;

/**
 * 服务工单「基础信息」只读区需要的客户资料（跨模块出参）。
 *
 * <p>只给客服工作台与主管审查页使用：昵称 + 联系方式，不含密码哈希等敏感字段。
 * 客户不存在或已软删时返回 null，由调用方兜底展示「客户 ID」。</p>
 */
public record CustomerContact(long customerId, String nickname, String phone, String email) {
}
