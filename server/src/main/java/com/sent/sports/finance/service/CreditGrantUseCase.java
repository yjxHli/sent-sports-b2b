package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.service.dto.CreditGrantCmd;
import com.sent.sports.finance.service.dto.CreditGrantResult;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 企业授信用例（P3-42/43）：spec §6.8 / 验收——
 * 额度与账期、有效期（validFrom/validTo）；客户已存在授信则调整并保留已用额，否则新建。
 * 仅管理 CreditLine；订单模块后续据此读取 available() 做超额下单拦截。
 *
 * <p><b>收口修复</b>：授信结果必须同时落到 {@code enterprise_profile}。此前只写进程内
 * {@code InMemoryCreditLineRepository}，而客户列表的「授信额度」读的是真库，
 * 于是「确认授信成功」之后界面依然显示「未授信」。现在同一用例内补齐真库写面。</p>
 */
@Service
public class CreditGrantUseCase {

    private final CreditLineRepository creditLineRepository;
    private final EnterpriseProfileMapper enterpriseProfileMapper;

    public CreditGrantUseCase(CreditLineRepository creditLineRepository,
                              EnterpriseProfileMapper enterpriseProfileMapper) {
        this.creditLineRepository = creditLineRepository;
        this.enterpriseProfileMapper = enterpriseProfileMapper;
    }

    @UseCase
    public CreditGrantResult grant(CreditGrantCmd cmd) {
        if (cmd.customerId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (cmd.totalCent() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (cmd.validToMillis() <= cmd.validFromMillis()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }

        CreditLine line = creditLineRepository.findByCustomerId(cmd.customerId())
                .map(existing -> {
                    existing.update(cmd.totalCent(), cmd.accountPeriodDays(),
                            cmd.validFromMillis(), cmd.validToMillis());
                    return existing;
                })
                .orElseGet(() -> new CreditLine(cmd.customerId(), cmd.totalCent(), 0L,
                        cmd.accountPeriodDays(), cmd.validFromMillis(), cmd.validToMillis(),
                        CreditLine.STATUS_ACTIVE));
        creditLineRepository.save(line);
        persistToProfile(cmd);

        return new CreditGrantResult(cmd.customerId(), line.getTotalCent(),
                line.available(), line.getValidToMillis(), line.getStatus());
    }

    /**
     * 授信写库：已有企业档案走 UPDATE（保留已用额度），无档案则补建。
     * <p>{@code company_name} 是 NOT NULL 列，补建时用「企业客户+ID」兜底，
     * 后续企业资料维护会覆盖成真实工商名称。</p>
     */
    private void persistToProfile(CreditGrantCmd cmd) {
        long expireAtMillis = Math.max(cmd.validToMillis(), 0L);
        if (enterpriseProfileMapper.updateCredit(cmd.customerId(), cmd.totalCent(),
                cmd.accountPeriodDays(), expireAtMillis) > 0) {
            return;
        }
        enterpriseProfileMapper.insertCredit(cmd.customerId(), "企业客户" + cmd.customerId(),
                cmd.totalCent(), cmd.accountPeriodDays(), expireAtMillis);
    }

    /** 便于其他用例复用「取授信额度」的读法，避免各处重复拼装 Optional。 */
    public Optional<CreditLine> find(long customerId) {
        return creditLineRepository.findByCustomerId(customerId);
    }
}
