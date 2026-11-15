package com.sent.sports.product.controller;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.product.dto.FavoriteDtos;
import com.sent.sports.product.service.FootprintService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品浏览足迹接口（{@code /api/footprints}，2026-11-16）。
 *
 * <p>写入是埋点语义：商品详情页一进来就 {@code POST}，服务端按商品去重。
 * 未登录时静默成功（不落库也不报错），避免详情页对匿名用户抛 401 红字。</p>
 */
@RestController
@RequestMapping("/api/footprints")
public class FootprintController {

    private final FootprintService footprintService;

    public FootprintController(FootprintService footprintService) {
        this.footprintService = footprintService;
    }

    /** 我的足迹。 */
    @GetMapping
    public R<PageResult<FavoriteDtos.FootprintVO>> list(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @ModelAttribute PageQuery page) {
        return R.ok(footprintService.list(customerIdOf(user), page));
    }

    /** 足迹总数（个人中心三联卡）。 */
    @GetMapping("/count")
    public R<Long> count(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(footprintService.count(customerIdOf(user)));
    }

    /** 记录一次浏览。匿名访问静默返回成功。 */
    @PostMapping("/{productId}")
    public R<Void> record(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @PathVariable long productId) {
        long customerId = usableCustomerId(user);
        if (customerId > 0) {
            footprintService.record(customerId, productId);
        }
        return R.ok();
    }

    /** 清空足迹，返回实际删除条数。 */
    @DeleteMapping
    public R<Integer> clear(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(footprintService.clear(customerIdOf(user)));
    }

    /** 读接口：必须登录，否则无从知道「谁的足迹」。 */
    private long customerIdOf(CurrentUser user) {
        long id = usableCustomerId(user);
        if (id <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return id;
    }

    private long usableCustomerId(CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            return 0L;
        }
        return user.userId();
    }
}
