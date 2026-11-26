package com.sent.sports.review.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.review.dto.admin.AdminReviewDtos;
import com.sent.sports.review.service.ReviewAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价管理（V56）：商品 → 评价管理。
 *
 * <p>权限点 {@code system:review:manage} 以 <b>exact</b> 方式校验：
 * 演示库里销售/财务/仓管等业务角色都持有 {@code '*'}，若不精确匹配，
 * 他们点 URL 就能进治理页——「只有超管和运营可见」形同虚设。</p>
 *
 * <p>红线：这两个端点只做「查询」与「改状态」，用户端提交评价的资格/防重/校验逻辑一行未动。</p>
 */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewAdminService reviewAdminService;

    public AdminReviewController(ReviewAdminService reviewAdminService) {
        this.reviewAdminService = reviewAdminService;
    }

    /**
     * 分页查询。
     *
     * @param productId 按商品筛选，空表示全部
     * @param rating    按星级精确筛选 1~5，空表示全部
     * @param status    NORMAL / HIDDEN，空表示全部
     */
    @GetMapping
    @RequirePerm(value = "system:review:manage", exact = true)
    public R<PageResult<AdminReviewDtos.AdminReviewVO>> list(@RequestParam(required = false) Long productId,
                                                             @RequestParam(required = false) Integer rating,
                                                             @RequestParam(required = false) String status,
                                                             @ModelAttribute PageQuery page) {
        return R.ok(reviewAdminService.list(productId, rating, status, page));
    }

    /** 隐藏 / 恢复：状态变更落 sys_operation_log。 */
    @PutMapping("/{id}/status")
    @RequirePerm(value = "system:review:manage", exact = true)
    public R<AdminReviewDtos.AdminReviewVO> updateStatus(
            @PathVariable long id,
            @Valid @RequestBody AdminReviewDtos.StatusCmd cmd,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        return R.ok(reviewAdminService.updateStatus(id, cmd.status(), cmd.reason(),
                user == null ? null : user.userId()));
    }
}
