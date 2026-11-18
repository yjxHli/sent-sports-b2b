package com.sent.sports.coupon.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.dto.IssueCouponCmd;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 管理端发券：统一发放或指定客户，并同步写 PROMO 站内信。 */
@Service
public class CouponIssueUseCase {

    private final CouponTemplateMapper templateMapper;
    private final CouponMapper couponMapper;
    private final MessageSendService messageSendService;

    public CouponIssueUseCase(CouponTemplateMapper templateMapper, CouponMapper couponMapper,
                              MessageSendService messageSendService) {
        this.templateMapper = templateMapper;
        this.couponMapper = couponMapper;
        this.messageSendService = messageSendService;
    }

    /** @return 实际发放张数 */
    @UseCase
    public int issue(IssueCouponCmd cmd) {
        CouponTemplatePO template = templateMapper.selectById(cmd.templateId());
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "券模板不存在或已停用");
        }
        List<Long> targets = (cmd.customerIds() == null || cmd.customerIds().isEmpty())
                ? defaultTargets(template) : cmd.customerIds();
        // null = 沿用模板门槛；显式 0 = 无门槛。用 long 的 0 兼任两义会让运营发不出无门槛券。
        long threshold = cmd.thresholdCent() == null ? template.getThresholdCent() : cmd.thresholdCent();
        LocalDateTime now = LocalDateTime.now();
        int issued = 0;
        for (Long customerId : targets) {
            if (customerId == null || customerId <= 0) continue;
            String couponNo = newCouponNo();
            CustomerCouponPO po = new CustomerCouponPO();
            po.setCustomerId(customerId);
            po.setTemplateId(template.getId());
            po.setCouponNo(couponNo);
            po.setName(template.getName());
            po.setAmountCent(template.getAmountCent());
            po.setThresholdCent(threshold);
            po.setCouponType(template.getCouponType());
            po.setDiscountType(template.getDiscountType());
            po.setDiscountRate(template.getDiscountRate());
            po.setScopeType(template.getScopeType());
            po.setScopeIds(template.getScopeIds());
            po.setCustomerScope(template.getCustomerScope());
            po.setStatus(Coupon.UNUSED);
            po.setSource(cmd.scene() == null ? "ACTIVITY" : cmd.scene());
            po.setReason(cmd.reason());
            po.setExpireAt(now.plusDays(template.getValidDays() > 0 ? template.getValidDays() : 30));
            couponMapper.insert(po);
            messageSendService.send(new InboxMessage(customerId, "PROMO", "优惠券已到账",
                    messageText(template.getName(), cmd), "COUPON", couponNo));
            issued++;
        }
        return issued;
    }

    /** 券号全局唯一即可，UUID 兜底不依赖 DB 序列（uk_coupon_no 是最终防重真源）。 */
    static String newCouponNo() {
        return "CPN" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /** 未指定客户时的发放范围：企业专属券只发企业客户，其余发全体有效客户。 */
    private List<Long> defaultTargets(CouponTemplatePO template) {
        return Coupon.CUSTOMER_ENTERPRISE.equals(template.getCustomerScope())
                ? templateMapper.selectAllEnterpriseCustomerIds()
                : templateMapper.selectAllCustomerIds();
    }

    private static String messageText(String couponName, IssueCouponCmd cmd) {
        if ("AFTER_SALE".equals(cmd.scene())) {
            String reason = cmd.reason() == null || cmd.reason().isBlank()
                    ? "客服售后补偿" : cmd.reason().trim();
            return "平台补充：" + couponName + "优惠券已发放，原因：" + reason;
        }
        return "平台活动：" + couponName + "优惠券发放";
    }
}
