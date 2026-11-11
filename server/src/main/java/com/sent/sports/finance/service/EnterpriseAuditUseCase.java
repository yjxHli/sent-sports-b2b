package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.service.dto.EnterpriseAuditResult;
import org.springframework.stereotype.Service;

/**
 * 企业认证审核用例：把「审核通过 / 驳回」落成 {@code enterprise_profile.status}。
 * <p>背景：管理端「企业审核与授信」的两个按钮此前直接打一个不存在的端点，
 * 通过与驳回都会看到「系统异常」。这里补齐真库写面，并让档案缺失明确回 40401。</p>
 */
@Service
public class EnterpriseAuditUseCase {

    /** 审核通过：企业档案可用，可继续走授信。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 审核驳回：资料不齐，需重新提交。 */
    public static final String STATUS_REJECTED = "REJECTED";

    private final EnterpriseProfileMapper enterpriseProfileMapper;

    public EnterpriseAuditUseCase(EnterpriseProfileMapper enterpriseProfileMapper) {
        this.enterpriseProfileMapper = enterpriseProfileMapper;
    }

    /**
     * 审核企业认证。
     *
     * @param customerId 企业客户号
     * @param approved   true=通过，false=驳回
     * @param remark     审核意见（当前只做留痕入口，不写库，审计日志由 system 模块统一承担）
     */
    @UseCase
    public EnterpriseAuditResult audit(long customerId, boolean approved, String remark) {
        if (customerId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "企业客户ID不合法");
        }
        String status = approved ? STATUS_ACTIVE : STATUS_REJECTED;
        if (enterpriseProfileMapper.updateStatus(customerId, status) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到该企业的认证档案，无法审核");
        }
        return new EnterpriseAuditResult(customerId, status, approved);
    }
}
