package com.sent.sports.system.controller.admin;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.system.dto.admin.AdminMeVO;
import com.sent.sports.system.service.StaffProfileQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端当前员工控制器（收口阶段）：{@code GET /api/admin/me}。
 * <p>用户端 {@code /api/me} 返回客户资料，管理端单独走本端点返回员工 + 角色 + 权限 + 数据范围，
 * 两种身份的响应形状各自稳定。非 admin 令牌按匿名拒绝（40101）。</p>
 */
@RestController
@RequestMapping("/api/admin/me")
public class AdminMeController {

    private final StaffProfileQueryService staffProfileQueryService;

    public AdminMeController(StaffProfileQueryService staffProfileQueryService) {
        this.staffProfileQueryService = staffProfileQueryService;
    }

    @GetMapping
    public R<AdminMeVO> me(@RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        if (user == null || !user.isAdmin()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return R.ok(staffProfileQueryService.profile(user.userId()));
    }
}
