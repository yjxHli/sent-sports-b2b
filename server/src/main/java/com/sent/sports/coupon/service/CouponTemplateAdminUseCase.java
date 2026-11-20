package com.sent.sports.coupon.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.domain.CouponType;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.service.dto.CreateCouponTemplateCmd;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端自建券模板（2026-11-18 用户需求）：创建「特定商品」的券，或删除自建券。
 *
 * <p>四类券型的约束在 {@link CouponType} 里兜住（R7），避免运营配出一张「永远抵 0」的券：</p>
 * <ul>
 *   <li>品类券必须选 SPU/SKU，否则等价于全场券；</li>
 *   <li>折扣券的比例必须是真的折扣（1~99），100 等于没打折；</li>
 *   <li>满减 / 企业券必须有门槛，否则等于发钱；</li>
 *   <li>企业专属券一律落 {@code ENTERPRISE} 客户范围，跟着券型走而不是跟着表单走。</li>
 * </ul>
 * <p>本类只负责字段清洗、默认值与落库；新增券型时不需要再来这里加分支。</p>
 *
 * <p>删除是软删：已发出的券是快照，删模板不影响历史券与订单抵扣。</p>
 */
@Service
public class CouponTemplateAdminUseCase {

    private static final int DEFAULT_VALID_DAYS = 30;
    private static final int MAX_NAME_LEN = 64;

    private final CouponTemplateMapper templateMapper;
    private final IdGen idGen;

    public CouponTemplateAdminUseCase(CouponTemplateMapper templateMapper, IdGen idGen) {
        this.templateMapper = templateMapper;
        this.idGen = idGen;
    }

    /** @return 新建模板的主键与券号（运营拿券号就能在发放下拉里找到它） */
    @UseCase
    public CouponDtos.CreateTemplateResult create(CreateCouponTemplateCmd cmd, long staffId) {
        String name = cmd.name() == null ? "" : cmd.name().trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写券名称");
        }
        if (name.length() > MAX_NAME_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "券名称最多 " + MAX_NAME_LEN + " 个字符");
        }
        String couponType = cmd.couponType() == null ? "" : cmd.couponType().trim();
        // 券型即策略：范围/门槛/客户域的规则都在枚举里，这里只做字段清洗与落库
        CouponType type = CouponType.of(couponType);
        String discountType = Coupon.DISCOUNT_PERCENT.equals(cmd.discountType())
                ? Coupon.DISCOUNT_PERCENT : Coupon.DISCOUNT_AMOUNT;
        int discountRate = 100;
        long amountCent = 0L;
        if (Coupon.DISCOUNT_PERCENT.equals(discountType)) {
            if (cmd.discountRate() <= 0 || cmd.discountRate() >= 100) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "折扣比例需在 1~99 之间（90 表示 9 折）");
            }
            discountRate = cmd.discountRate();
        } else {
            if (cmd.amountCent() <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请填写券面额");
            }
            amountCent = cmd.amountCent();
        }

        String scopeType = normalizeScopeType(cmd.scopeType());
        String scopeIds = normalizeScopeIds(cmd.scopeIds());
        long thresholdCent = Math.max(0L, cmd.thresholdCent());
        type.validateTemplate(scopeType, scopeIds, thresholdCent);
        thresholdCent = type.normalizeThreshold(thresholdCent);
        if (Coupon.SCOPE_ALL.equals(scopeType)) {
            scopeIds = null;
        } else if (scopeIds == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择适用商品");
        }

        CouponTemplatePO po = new CouponTemplatePO();
        po.setTemplateNo(idGen.next("CPT"));
        po.setName(name);
        po.setCouponType(type.code());
        po.setDiscountType(discountType);
        po.setDiscountRate(discountRate);
        po.setAmountCent(amountCent);
        po.setThresholdCent(thresholdCent);
        po.setScopeType(scopeType);
        po.setScopeIds(scopeIds);
        // 客户域跟着券型走，表单传 ALL 也纠正回来
        po.setCustomerScope(type.customerScope());
        po.setClaimable(cmd.claimable() == 1 ? 1 : 0);
        po.setClaimLimit(1);
        // 0 / 负数都按「不限量」处理：领券中心一张都不发没有意义
        po.setTotalQty(cmd.totalQty() <= 0 ? -1 : cmd.totalQty());
        po.setClaimedQty(0);
        po.setValidDays(cmd.validDays() > 0 ? cmd.validDays() : DEFAULT_VALID_DAYS);
        po.setScene("ADMIN");
        String status = cmd.status() == null ? "" : cmd.status().trim().toUpperCase();
        po.setStatus(status.isEmpty() ? "ACTIVE" : status);
        po.setCreatedBy(staffId > 0L ? staffId : null);
        po.setDeleted(0);
        templateMapper.insert(po);
        return new CouponDtos.CreateTemplateResult(po.getId(), po.getTemplateNo());
    }

    /** @return 实际软删条数 */
    @UseCase
    public int delete(List<String> ids) {
        return templateMapper.batchMarkDeleted(BatchIds.normalize(ids));
    }

    private static String normalizeScopeType(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        return switch (value) {
            case Coupon.SCOPE_SPU -> Coupon.SCOPE_SPU;
            case Coupon.SCOPE_SKU -> Coupon.SCOPE_SKU;
            default -> Coupon.SCOPE_ALL;
        };
    }

    /** 「9101, 9102 ,9101」→「9101,9102」，保证库里不出现重复或空白主键 */
    private static String normalizeScopeIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> ids = java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        return ids.isEmpty() ? null : String.join(",", ids);
    }
}
