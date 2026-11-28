package com.sent.sports.finance.service.impl;

import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.util.TimeUtil;
import com.sent.sports.finance.dto.UserEnterpriseDtos;
import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.po.MyContractRow;
import com.sent.sports.finance.mapper.po.MyEnterpriseRow;
import com.sent.sports.finance.service.UserEnterpriseQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户端企业工具查询实现。
 *
 * <p><b>为什么没有档案返回 NONE 而不是 404</b>：个人客户点进「企业工具」是正常路径，
 * 不是异常；前端据此渲染「资质不够 + 去申请企业认证」。若抛 404，页面只能显示报错。</p>
 *
 * <p><b>可用额度</b>取 {@code max(0, 总额 - 已用)}：历史数据里存在已用大于总额的情况
 * （手工改库或旧上限下调），透出负数会让用户以为欠款，实际只是占用超额。</p>
 */
@Service
public class UserEnterpriseQueryServiceImpl implements UserEnterpriseQueryService {

    /** 还不是企业客户（没有 enterprise_profile 档案）。 */
    private static final String STATUS_NONE = "NONE";
    private static final int DEFAULT_DISCOUNT_RATE = 100;

    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final ContractMapper contractMapper;

    public UserEnterpriseQueryServiceImpl(EnterpriseProfileMapper enterpriseProfileMapper,
                                          ContractMapper contractMapper) {
        this.enterpriseProfileMapper = enterpriseProfileMapper;
        this.contractMapper = contractMapper;
    }

    @Override
    public UserEnterpriseDtos.OverviewVO overview(long customerId) {
        MyEnterpriseRow profile = customerId <= 0L ? null : enterpriseProfileMapper.selectMyProfile(customerId);
        if (profile == null) {
            return new UserEnterpriseDtos.OverviewVO(STATUS_NONE, null, null,
                    0L, 0L, 0L, 0, 0L, null, 0L, 0L, List.of());
        }
        long limit = nz(profile.getCreditLimitCent());
        long used = nz(profile.getCreditUsedCent());
        long available = Math.max(0L, limit - used);
        return new UserEnterpriseDtos.OverviewVO(
                profile.getStatus(),
                profile.getCompanyName(),
                profile.getCreditCode(),
                limit,
                used,
                available,
                profile.getPaymentTermDays() == null ? 0 : profile.getPaymentTermDays(),
                TimeUtil.toMillis(profile.getCreditExpireAt()),
                profile.getAuditRemark(),
                TimeUtil.toMillis(profile.getAuditedAt()),
                TimeUtil.toMillis(profile.getSubmittedAt()),
                contractsOf(customerId));
    }

    private List<UserEnterpriseDtos.ContractItemVO> contractsOf(long customerId) {
        return contractMapper.selectMyContracts(customerId).stream()
                .map(this::toContractVo)
                .toList();
    }

    private UserEnterpriseDtos.ContractItemVO toContractVo(MyContractRow row) {
        long amountCent = nz(row.getAmountCent());
        return new UserEnterpriseDtos.ContractItemVO(
                row.getContractNo(),
                amountCent,
                MoneyUtil.toYuan(amountCent),
                row.getStatus(),
                row.getSignDate() == null ? null : row.getSignDate().toString(),
                row.getExpireDate() == null ? null : row.getExpireDate().toString(),
                row.getDiscountRate() == null ? DEFAULT_DISCOUNT_RATE : row.getDiscountRate());
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }
}
