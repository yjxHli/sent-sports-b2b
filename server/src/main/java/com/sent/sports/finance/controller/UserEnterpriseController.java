package com.sent.sports.finance.controller;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.finance.dto.UserEnterpriseDtos;
import com.sent.sports.finance.service.UserEnterpriseQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端企业工具接口：个人中心 → 企业工具 / 我的授信 / 先采后付 / 我的合同。
 * <p>身份同 {@code MeController}：只认 {@code aud=user} 的令牌，客户号取自令牌，
 * 不接受请求参数，避免用参数看别家企业的授信与合同。</p>
 */
@RestController
@RequestMapping("/api/me/enterprise")
public class UserEnterpriseController {

    private final UserEnterpriseQueryService userEnterpriseQueryService;

    public UserEnterpriseController(UserEnterpriseQueryService userEnterpriseQueryService) {
        this.userEnterpriseQueryService = userEnterpriseQueryService;
    }

    @GetMapping
    public R<UserEnterpriseDtos.OverviewVO> overview(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return R.ok(userEnterpriseQueryService.overview(user.userId()));
    }
}
