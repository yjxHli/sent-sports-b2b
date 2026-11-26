package com.sent.sports.coupon.controller;

import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.service.CouponClaimUseCase;
import com.sent.sports.coupon.service.CouponQueryService;
import com.sent.sports.coupon.service.DeleteMyCouponUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 商城端「我的券 / 可用券 / 领券中心」。主体取 JWT，不接受前端传 customerId。 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponQueryService couponQueryService;
    private final CouponClaimUseCase couponClaimUseCase;
    private final DeleteMyCouponUseCase deleteMyCouponUseCase;

    public CouponController(CouponQueryService couponQueryService,
                            CouponClaimUseCase couponClaimUseCase,
                            DeleteMyCouponUseCase deleteMyCouponUseCase) {
        this.couponQueryService = couponQueryService;
        this.couponClaimUseCase = couponClaimUseCase;
        this.deleteMyCouponUseCase = deleteMyCouponUseCase;
    }

    @GetMapping("/mine")
    public R<List<CouponDtos.CouponVO>> mine(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(couponQueryService.listMine(customerId(user)));
    }

    /** 可用券：goodsCent 为商品额+定制费；skuIds 传购物车 SKU 才能判品类券范围。 */
    @GetMapping("/usable")
    public R<List<CouponDtos.CouponVO>> usable(
            @RequestParam(defaultValue = "0") long goodsCent,
            @RequestParam(required = false) List<Long> skuIds,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(couponQueryService.listUsable(customerId(user), goodsCent, skuIds));
    }

    /**
     * 用户删除自己的券：只接受已使用/已过期，软删不改券状态与订单折扣。
     * 主体取 JWT，前端不能指定 customerId；重复删除幂等。
     */
    @DeleteMapping("/{couponId}")
    public R<Void> delete(@PathVariable long couponId,
                          @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        deleteMyCouponUseCase.delete(couponId, customerId(user));
        return R.ok();
    }

    /** 领券中心：可领券列表（企业专属券对个人客户隐藏）。 */
    @GetMapping("/claimable")
    public R<List<CouponDtos.ClaimableVO>> claimable(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(couponQueryService.listClaimable(customerId(user)));
    }

    /** 领取一张券；重复领取返回业务拒绝（唯一索引 claim_key 兜底）。 */
    @PostMapping("/claim/{templateId}")
    public R<CouponDtos.ClaimResult> claim(
            @PathVariable long templateId,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        long customerId = customerId(user);
        if (customerId <= 0L) {
            return R.fail(ErrorCode.UNAUTHENTICATED.getCode(), "请先登录后再领取优惠券");
        }
        return R.ok(new CouponDtos.ClaimResult(couponClaimUseCase.claim(customerId, templateId)));
    }

    private static long customerId(CurrentUser user) {
        return user == null || user.isAdmin() ? 0L : user.userId();
    }
}
