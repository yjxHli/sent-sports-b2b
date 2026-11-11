package com.sent.sports.customize.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.customize.dto.admin.AdminCustomDtos;
import com.sent.sports.customize.service.CustomAdminQueryService;
import com.sent.sports.customize.service.ProduceCustomUseCase;
import com.sent.sports.customize.service.ReviewCustomUseCase;
import com.sent.sports.customize.service.dto.ProduceCustomCmd;
import com.sent.sports.customize.service.dto.ProduceCustomResult;
import com.sent.sports.customize.service.dto.ReviewCustomCmd;
import com.sent.sports.customize.service.dto.ReviewCustomResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定制审核控制器（P4-43）：待审列表 / 详情 / 通过 / 驳回 / 排产 / 完工（plan §8.11）。
 * <p>依赖同模块用例（{@link ReviewCustomUseCase} / {@link ProduceCustomUseCase}）
 * 与查询门面 {@link CustomAdminQueryService}（桩，P5 接入仓储）。数据范围（@DataScope）在 Phase 5。</p>
 */
@RestController
@RequestMapping("/api/admin/custom-orders")
public class AdminCustomController {

    private final ReviewCustomUseCase reviewCustomUseCase;
    private final ProduceCustomUseCase produceCustomUseCase;
    private final CustomAdminQueryService customAdminQueryService;

    public AdminCustomController(ReviewCustomUseCase reviewCustomUseCase,
                                ProduceCustomUseCase produceCustomUseCase,
                                CustomAdminQueryService customAdminQueryService) {
        this.reviewCustomUseCase = reviewCustomUseCase;
        this.produceCustomUseCase = produceCustomUseCase;
        this.customAdminQueryService = customAdminQueryService;
    }

    @GetMapping
    public R<PageResult<?>> pending(@ModelAttribute PageQuery page) {
        return R.ok(customAdminQueryService.pendingList(page));
    }

    @GetMapping("/{id}")
    public R<Object> detail(@PathVariable String id) {
        return R.ok(customAdminQueryService.detail(id));
    }

    @PostMapping("/{id}/pass")
    @RequirePerm("custom:review")
    public R<AdminCustomDtos.ReviewVO> pass(
            @PathVariable String id,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser operator) {
        ReviewCustomResult r = reviewCustomUseCase.review(
                new ReviewCustomCmd(id, "PASS", null, null, reviewerId(operator)));
        return R.ok(new AdminCustomDtos.ReviewVO(r.customOrderId(), r.status(), r.lockedVersion(), r.remainingMillis()));
    }

    @PostMapping("/{id}/reject")
    @RequirePerm("custom:review")
    public R<AdminCustomDtos.ReviewVO> reject(
            @PathVariable String id,
            @Valid @RequestBody AdminCustomDtos.RejectCmd cmd,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser operator) {
        ReviewCustomResult r = reviewCustomUseCase.review(
                new ReviewCustomCmd(id, "REJECT", cmd.reasonCode(), cmd.remark(), reviewerId(operator)));
        return R.ok(new AdminCustomDtos.ReviewVO(r.customOrderId(), r.status(), r.lockedVersion(), r.remainingMillis()));
    }

    /** 审核人取 AuthFilter 注入的 JWT sub；直接调用（无 Servlet 上下文）时留空。 */
    private static Long reviewerId(CurrentUser operator) {
        return operator == null ? null : operator.userId();
    }

    @PostMapping("/{id}/production")
    @RequirePerm("custom:produce")
    public R<AdminCustomDtos.ProduceVO> production(@PathVariable String id) {
        ProduceCustomResult r = produceCustomUseCase.produce(new ProduceCustomCmd(id, "START_PRODUCTION"));
        return R.ok(new AdminCustomDtos.ProduceVO(r.customOrderId(), r.status(), r.startedAtMillis(), r.finishedAtMillis()));
    }

    @PostMapping("/{id}/finish")
    @RequirePerm("custom:produce")
    public R<AdminCustomDtos.ProduceVO> finish(@PathVariable String id) {
        ProduceCustomResult r = produceCustomUseCase.produce(new ProduceCustomCmd(id, "FINISH"));
        return R.ok(new AdminCustomDtos.ProduceVO(r.customOrderId(), r.status(), r.startedAtMillis(), r.finishedAtMillis()));
    }
}
