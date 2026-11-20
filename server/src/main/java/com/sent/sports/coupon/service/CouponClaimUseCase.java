package com.sent.sports.coupon.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.customer.service.CustomerQueryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 领券中心领取。
 *
 * <p>防重三件套：企业专属券先判客户类型；库存用带条件的
 * {@code UPDATE ... WHERE claimed_qty < total_qty} 原子扣减；最后靠
 * {@code uk_coupon_claim}（customer_id + claim_key）唯一索引兜住并发重复领取。
 * 全程不用分布式锁。</p>
 */
@Service
public class CouponClaimUseCase {

    private static final String CUSTOMER_ENTERPRISE = "ENTERPRISE";
    private static final String SOURCE_CLAIM = "CLAIM";
    private static final int DEFAULT_VALID_DAYS = 30;

    private final CouponTemplateMapper templateMapper;
    private final CouponMapper couponMapper;
    private final CustomerQueryService customerQueryService;
    private final Clock clock;

    public CouponClaimUseCase(CouponTemplateMapper templateMapper, CouponMapper couponMapper,
                              CustomerQueryService customerQueryService, Clock clock) {
        this.templateMapper = templateMapper;
        this.couponMapper = couponMapper;
        this.customerQueryService = customerQueryService;
        this.clock = clock;
    }

    /** @return 领取到的券号 */
    @UseCase
    public String claim(long customerId, long templateId) {
        CouponTemplatePO template = templateMapper.selectById(templateId);
        if (template == null || !"ACTIVE".equals(template.getStatus()) || template.getClaimable() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "该优惠券不存在或已停止领取");
        }
        if (Coupon.CUSTOMER_ENTERPRISE.equals(template.getCustomerScope())
                && !CUSTOMER_ENTERPRISE.equals(customerQueryService.typeOf(customerId))) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该券为企业专属券，仅企业客户可领取");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 会员日门禁：仅周三/周四可从领券中心领券。必须放在扣库存之前，
        // 否则非会员日请求会白白扣掉库存却领不到券。
        if (!MemberDayPolicy.isMemberDay(now.toLocalDate())) {
            throw new BizException(ErrorCode.BIZ_REJECT,
                    "仅会员日（每周三、周四）可领取优惠券，请使用会员日当天领取");
        }
        // 原子扣减：影响行数 0 表示已领完（并发下也只有一个请求能拿到 1）
        if (templateMapper.increaseClaimed(templateId) == 0) {
            throw new BizException(ErrorCode.BIZ_REJECT, "该优惠券已领完");
        }
        CustomerCouponPO po = new CustomerCouponPO();
        po.setCustomerId(customerId);
        po.setTemplateId(templateId);
        po.setCouponNo(CouponIssueUseCase.newCouponNo());
        po.setName(template.getName());
        po.setAmountCent(template.getAmountCent());
        po.setThresholdCent(template.getThresholdCent());
        po.setCouponType(template.getCouponType());
        po.setDiscountType(template.getDiscountType());
        po.setDiscountRate(template.getDiscountRate());
        po.setScopeType(template.getScopeType());
        po.setScopeIds(template.getScopeIds());
        po.setCustomerScope(template.getCustomerScope());
        po.setClaimKey(claimKey(templateId));
        po.setStatus(Coupon.UNUSED);
        po.setSource(SOURCE_CLAIM);
        po.setReason(null);
        int validDays = MemberDayPolicy.claimValidDays(
                template.getCouponType(), template.getAmountCent(), template.getValidDays());
        po.setExpireAt(now.plusDays(validDays > 0 ? validDays : DEFAULT_VALID_DAYS));
        try {
            couponMapper.insert(po);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.BIZ_REJECT, "您已领取过该优惠券");
        }
        return po.getCouponNo();
    }

    /** 领取凭证：一个客户对一个模板只能有一张。 */
    static String claimKey(long templateId) {
        return "CLAIM-" + templateId;
    }
}
