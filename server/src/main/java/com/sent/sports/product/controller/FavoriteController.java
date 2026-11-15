package com.sent.sports.product.controller;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.product.dto.FavoriteDtos;
import com.sent.sports.product.service.FavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品收藏接口（{@code /api/favorites}，2026-11-16）。
 *
 * <p>身份一律取 {@code AuthFilter} 注入的 {@link CurrentUser}（aud=user），
 * 不接受请求体传 customerId；未登录访问收藏夹直接 401，让前端跳登录而不是显示空列表。</p>
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** 我的收藏夹。 */
    @GetMapping
    public R<PageResult<FavoriteDtos.FavoriteVO>> list(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @ModelAttribute PageQuery page) {
        return R.ok(favoriteService.list(customerIdOf(user), page));
    }

    /** 收藏总数（个人中心三联卡）。 */
    @GetMapping("/count")
    public R<Long> count(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(favoriteService.count(customerIdOf(user)));
    }

    /** 某商品是否已收藏（详情页心形按钮初始态）。 */
    @GetMapping("/status")
    public R<FavoriteDtos.FavoriteStatusVO> status(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @RequestParam long productId) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            // 未登录时前端只需要知道「还没收藏」，不必抛 401 打断详情页渲染
            return R.ok(new FavoriteDtos.FavoriteStatusVO(false, 0L));
        }
        return R.ok(new FavoriteDtos.FavoriteStatusVO(
                favoriteService.favorited(user.userId(), productId),
                favoriteService.count(user.userId())));
    }

    /** 收藏。重复收藏幂等成功。 */
    @PostMapping("/{productId}")
    public R<Boolean> add(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @PathVariable long productId) {
        return R.ok(favoriteService.add(customerIdOf(user), productId));
    }

    /** 取消收藏。未收藏时返回 false，不报错。 */
    @DeleteMapping("/{productId}")
    public R<Boolean> remove(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @PathVariable long productId) {
        return R.ok(favoriteService.remove(customerIdOf(user), productId));
    }

    private long customerIdOf(CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user.userId();
    }
}
