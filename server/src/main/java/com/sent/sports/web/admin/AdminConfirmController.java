package com.sent.sports.web.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.dto.admin.ConfirmDtos;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 危险操作确认令牌控制器（P4-58）：换取一次性确认令牌（plan §10.1）。
 * <p>依赖 {@link ConfirmTokenService}（接口，位于 common，避免 web.admin 直接依赖业务模块）。
 * 写端点标 {@code @RequirePerm("confirm:token:issue")}（P5 拦截器生效前无害）。
 * 真正"校验令牌 + 用后即焚"由业务接口在 P5 通过 X-Confirm-Token 头完成。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminConfirmController {

    private final ConfirmTokenService confirmTokenService;

    public AdminConfirmController(ConfirmTokenService confirmTokenService) {
        this.confirmTokenService = confirmTokenService;
    }

    @PostMapping("/confirm-token")
    @RequirePerm("confirm:token:issue")
    public R<ConfirmDtos.TokenVO> issue(@Valid @RequestBody ConfirmDtos.ConfirmCmd cmd) {
        return R.ok(confirmTokenService.issue(cmd));
    }
}
