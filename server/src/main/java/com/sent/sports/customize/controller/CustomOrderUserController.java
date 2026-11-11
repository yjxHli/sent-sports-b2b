package com.sent.sports.customize.controller;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.service.CustomQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端「我的定制单」控制器（收口阶段）：{@code GET /api/custom-orders/my}。
 * <p>从 {@code AuthFilter} 注入的 {@link CurrentUser} 取客户身份（aud=user），
 * 经同模块查询门面读取分页列表（联查订单状态与图稿版本）。未携带 user 端令牌按匿名拒绝（40101）。</p>
 */
@RestController
@RequestMapping("/api/custom-orders")
public class CustomOrderUserController {

    private final CustomQueryService customQueryService;

    public CustomOrderUserController(CustomQueryService customQueryService) {
        this.customQueryService = customQueryService;
    }

    @GetMapping("/my")
    public R<PageResult<CustomDtos.MyCustomOrderVO>> my(@RequestAttribute(value = CurrentUser.ATTR,
            required = false) CurrentUser user, @ModelAttribute PageQuery pageQuery) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return R.ok(customQueryService.myOrders(user.userId(), pageQuery));
    }
}
