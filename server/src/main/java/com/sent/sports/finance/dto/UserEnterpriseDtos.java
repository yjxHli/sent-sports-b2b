package com.sent.sports.finance.dto;

import java.util.List;

/**
 * 用户端企业工具接口 DTO（个人中心 → 企业工具）。
 * <p>只透出当前登录客户自己的企业档案、授信与合同；客户身份取自令牌，不接受请求参数。</p>
 */
public final class UserEnterpriseDtos {

    private UserEnterpriseDtos() {
    }

    /**
     * 企业概况（{@code GET /api/me/enterprise}）。
     *
     * @param status 认证状态：NONE（还不是企业客户）/ PENDING / ACTIVE / REJECTED
     * @param creditLimitCent 授信总额（分）
     * @param creditUsedCent  已占用额度（分）
     * @param creditAvailableCent 可用额度（分，下限 0）
     * @param paymentTermDays 账期天数（先采后付）
     * @param creditExpireAtMillis 授信到期时间（0=未设置）
     * @param auditRemark 审核意见（驳回原因）
     * @param contracts 我的合同（无档案时为空列表）
     */
    public record OverviewVO(
            String status,
            String companyName,
            String creditCode,
            long creditLimitCent,
            long creditUsedCent,
            long creditAvailableCent,
            int paymentTermDays,
            long creditExpireAtMillis,
            String auditRemark,
            long auditedAtMillis,
            long submittedAtMillis,
            List<ContractItemVO> contracts) {
    }

    /** 合同行：日期用 ISO 字符串（{@code yyyy-MM-dd}），无到期日为 null。 */
    public record ContractItemVO(
            String contractNo,
            long amountCent,
            String amountYuan,
            String status,
            String signDate,
            String expireDate,
            int discountRate) {
    }
}
