package com.sent.sports.chat.controller.admin;

import com.sent.sports.chat.dto.admin.AdminFaqDtos;
import com.sent.sports.chat.service.FaqAdminService;
import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
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
 * 智能客服话术配置（V60）：客服管理 → 话术配置。
 *
 * <p>权限点 {@code chat:faq:manage} 以 <b>exact</b> 方式校验：演示库里客服、销售、财务等角色
 * 都持有 {@code '*'}，若不精确匹配，他们改一句话术就能影响所有客户看到的自动回复——
 * 这是内容事故，不是权限小瑕疵。</p>
 *
 * <p>写接口（新增 / 修改 / 启停）在服务层最后一步刷新知识库快照，
 * 所以运营点完保存，客户下一条消息就用新话术，<b>不需要重启也不需要等缓存过期</b>。</p>
 */
@RestController
@RequestMapping("/api/admin/faq")
public class AdminFaqController {

    private final FaqAdminService faqAdminService;

    public AdminFaqController(FaqAdminService faqAdminService) {
        this.faqAdminService = faqAdminService;
    }

    /**
     * 分页查询。
     *
     * @param keyword 关键词 / 回复正文模糊搜
     * @param status  ENABLED / DISABLED，空表示全部
     */
    @GetMapping
    @RequirePerm(value = "chat:faq:manage", exact = true)
    public R<PageResult<AdminFaqDtos.FaqVO>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @ModelAttribute PageQuery page) {
        return R.ok(faqAdminService.list(keyword, status, page));
    }

    /** 新增话术：保存即生效。 */
    @PostMapping
    @RequirePerm(value = "chat:faq:manage", exact = true)
    public R<AdminFaqDtos.FaqVO> create(@Valid @RequestBody AdminFaqDtos.FaqCmd cmd,
                                        @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(faqAdminService.create(cmd, user == null ? null : user.userId()));
    }

    /** 修改话术：保存即生效。 */
    @PutMapping("/{id}")
    @RequirePerm(value = "chat:faq:manage", exact = true)
    public R<AdminFaqDtos.FaqVO> update(@PathVariable long id,
                                        @Valid @RequestBody AdminFaqDtos.FaqCmd cmd,
                                        @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(faqAdminService.update(id, cmd, user == null ? null : user.userId()));
    }

    /** 启用 / 停用：停用后机器人不再用这条话术，条目与命中次数保留。 */
    @PutMapping("/{id}/status")
    @RequirePerm(value = "chat:faq:manage", exact = true)
    public R<AdminFaqDtos.FaqVO> updateStatus(@PathVariable long id,
                                              @Valid @RequestBody AdminFaqDtos.StatusCmd cmd,
                                              @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(faqAdminService.updateStatus(id, cmd.status(), user == null ? null : user.userId()));
    }

    /**
     * 批量删除（V62）：与发票 / 审查记录同口径——进入删除态 → 勾选 → 输入「删除」换一次性令牌 → 软删。
     *
     * <p>{@link Confirm#action()} 同时决定确认短语与令牌绑定的目标串：
     * 前端按「trim → 去空 → 去重 → 字典序升序」把勾选的 ID 拼成 targetId，
     * 这里由拦截器用同一口径复算，对不上就是 40302（不是权限问题，是目标不一致）。</p>
     */
    @PostMapping("/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm(value = "chat:faq:manage", exact = true)
    public R<BatchDeleteDtos.BatchDeleteVO> batchDelete(@Valid @RequestBody BatchDeleteDtos.BatchDeleteCmd cmd,
                                                        @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(new BatchDeleteDtos.BatchDeleteVO(
                faqAdminService.delete(cmd.ids(), user == null ? null : user.userId())));
    }
}
