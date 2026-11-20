package com.sent.sports.system.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.system.dto.admin.SensitiveWordDtos;
import com.sent.sports.system.service.SensitiveWordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 违禁词库管理（V46）：系统管理 → 违禁词库。
 * <p>词库同时作用于「聊天消息」与「昵称/登录名」，保存后立即热重载，不需要重启服务。
 * 权限点 {@code system:sensitive:manage}：超管持 {@code *} 自动覆盖，其它角色需在权限矩阵里显式勾选。</p>
 */
@RestController
@RequestMapping("/api/admin/sensitive-words")
public class AdminSensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    public AdminSensitiveWordController(SensitiveWordService sensitiveWordService) {
        this.sensitiveWordService = sensitiveWordService;
    }

    /**
     * 分页 + 筛选。
     *
     * @param keyword  词/备注模糊匹配
     * @param category PROFANITY / AD / ILLEGAL / OTHER，空表示不限
     * @param enabled  1 只看启用、0 只看停用，空表示不限
     */
    @GetMapping
    @RequirePerm("system:sensitive:manage")
    public R<PageResult<SensitiveWordDtos.WordVO>> list(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String category,
                                                        @RequestParam(required = false) Integer enabled,
                                                        @ModelAttribute PageQuery page) {
        return R.ok(sensitiveWordService.list(keyword, category, enabled, page));
    }

    /** 顶部统计。固定路径要写在 /{id} 之前，否则会被当成 id 解析。 */
    @GetMapping("/stats")
    @RequirePerm("system:sensitive:manage")
    public R<SensitiveWordDtos.StatsVO> stats() {
        return R.ok(sensitiveWordService.stats());
    }

    @PostMapping
    @RequirePerm("system:sensitive:manage")
    public R<SensitiveWordDtos.WordVO> create(@Valid @RequestBody SensitiveWordDtos.WordForm form,
                                              @RequestAttribute(value = CurrentUser.ATTR, required = false)
                                              CurrentUser user) {
        return R.ok(sensitiveWordService.create(form, user == null ? null : user.userId()));
    }

    @PutMapping("/{id}")
    @RequirePerm("system:sensitive:manage")
    public R<SensitiveWordDtos.WordVO> update(@PathVariable long id,
                                              @Valid @RequestBody SensitiveWordDtos.WordForm form) {
        return R.ok(sensitiveWordService.update(id, form));
    }

    /** 启停开关：独立端点，只改 enabled，不覆盖别人正在编辑的备注/分类。 */
    @PutMapping("/{id}/enabled")
    @RequirePerm("system:sensitive:manage")
    public R<SensitiveWordDtos.WordVO> toggle(@PathVariable long id,
                                              @Valid @RequestBody ToggleForm form) {
        return R.ok(sensitiveWordService.toggle(id, form.enabled()));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("system:sensitive:manage")
    public R<Void> delete(@PathVariable long id) {
        sensitiveWordService.delete(id);
        return R.ok();
    }

    /** 启停入参（独立 record：避免为一个布尔值复用整张表单）。 */
    public record ToggleForm(@NotNull(message = "enabled 不能为空") Boolean enabled) {
    }
}
