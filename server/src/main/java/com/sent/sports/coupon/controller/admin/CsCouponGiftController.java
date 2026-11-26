package com.sent.sports.coupon.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.service.CustomerCouponGiftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客服工作台「优惠券发放」（V64）。
 *
 * <p>路径挂在 {@code /api/admin/chat/**} 下而不是 {@code /api/admin/coupons/**}，
 * 这是刻意的：权限点不同（{@code chat:serve} 而不是 {@code customer:grant}），
 * 受众也不同——客服能发小额补偿券，但不能进管理端的券模板与发放记录页。
 * 如果把两条路径混在一起，迟早有人为了「让客服能看到券」把 {@code customer:grant} 授给客服角色。</p>
 *
 * <p>20 元上限在服务端（{@link CustomerCouponGiftService}）判定，这里不做任何面额判断——
 * 控制器不承载业务规则，否则换个入口就又漏了。</p>
 */
@RestController
public class CsCouponGiftController {

    private final CustomerCouponGiftService giftService;

    public CsCouponGiftController(CustomerCouponGiftService giftService) {
        this.giftService = giftService;
    }

    /** 可发券档位（面额 ≤ 20 元）。 */
    @GetMapping("/api/admin/chat/coupons/options")
    @RequirePerm("chat:serve")
    public R<List<CouponDtos.TemplateVO>> options() {
        return R.ok(giftService.giftOptions());
    }

    /** 该客户的发券记录（含平台活动发的，客服据此判断是不是「又来要券」）。 */
    @GetMapping("/api/admin/chat/customers/{customerId}/coupons")
    @RequirePerm("chat:serve")
    public R<List<CouponDtos.GiftRecordVO>> records(
            @PathVariable long customerId,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return R.ok(giftService.recordsOf(customerId, limit));
    }

    /** 发券：直接进客户账户 + PROMO 站内信提醒，不发消息气泡（券是账户资产，不是聊天内容）。 */
    @PostMapping("/api/admin/chat/customers/{customerId}/coupons")
    @RequirePerm("chat:serve")
    public R<CouponDtos.GiftResultVO> gift(
            @PathVariable long customerId,
            @Valid @RequestBody CouponDtos.GiftCmd cmd,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(giftService.gift(customerId, cmd.templateId(), cmd.reason(),
                user == null ? null : user.userId()));
    }
}
