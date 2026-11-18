package com.sent.sports.coupon.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 新用户注册礼：自动发放 scene=REGISTER 的券模板（默认 5 无门槛 / 满20减10 / 满80减50），
 * 并用一条 PROMO 站内信汇总告知。由 RegisterUseCase 在同一事务内调用。
 */
@Service
public class RegisterCouponGrantUseCase implements RegisterCouponGrant {

    private final CouponTemplateMapper templateMapper;
    private final CouponMapper couponMapper;
    private final MessageSendService messageSendService;

    public RegisterCouponGrantUseCase(CouponTemplateMapper templateMapper, CouponMapper couponMapper,
                                      MessageSendService messageSendService) {
        this.templateMapper = templateMapper;
        this.couponMapper = couponMapper;
        this.messageSendService = messageSendService;
    }

    @Override
    @UseCase
    public void grant(long customerId) {
        List<CouponTemplatePO> templates = templateMapper.selectRegisterGifts();
        if (templates == null || templates.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        List<String> labels = new ArrayList<>();
        for (CouponTemplatePO template : templates) {
            CustomerCouponPO po = new CustomerCouponPO();
            po.setCustomerId(customerId);
            po.setTemplateId(template.getId());
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
            po.setStatus(Coupon.UNUSED);
            po.setSource("REGISTER");
            po.setExpireAt(now.plusDays(template.getValidDays() > 0 ? template.getValidDays() : 30));
            couponMapper.insert(po);
            labels.add(yuanText(template.getAmountCent()) + "元");
        }
        messageSendService.send(new InboxMessage(customerId, "PROMO", "优惠券已到账",
                "平台活动：新用户券包已发放（" + String.join("、", labels) + "）",
                "COUPON", "REGISTER-" + customerId));
    }

    private static String yuanText(long cent) {
        return String.valueOf(cent / 100);
    }
}
