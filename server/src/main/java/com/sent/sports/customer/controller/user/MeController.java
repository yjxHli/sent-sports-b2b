package com.sent.sports.customer.controller.user;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.customer.dto.user.MeVO;
import com.sent.sports.customer.service.CustomerProfileQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端个人资料控制器（收口阶段）：{@code GET /api/me}。
 * <p>从 {@code AuthFilter} 注入的 {@link CurrentUser} 请求属性取当前客户身份（aud=user），
 * 经同模块查询门面读取基本资料 / 等级 / 积分。未携带 user 端令牌一律按匿名拒绝（40101），
 * 前端据此跳转登录，与刷新令牌分流（40102）区分开。</p>
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CustomerProfileQueryService profileQueryService;

    public MeController(CustomerProfileQueryService profileQueryService) {
        this.profileQueryService = profileQueryService;
    }

    @GetMapping
    public R<MeVO> me(@RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return R.ok(profileQueryService.profile(user.userId()));
    }
}
