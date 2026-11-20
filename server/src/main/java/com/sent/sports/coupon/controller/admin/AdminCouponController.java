package com.sent.sports.coupon.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.service.CouponIssueUseCase;
import com.sent.sports.coupon.service.CouponQueryService;
import com.sent.sports.coupon.service.CouponTemplateAdminUseCase;
import com.sent.sports.coupon.service.dto.CreateCouponTemplateCmd;
import com.sent.sports.coupon.service.dto.IssueCouponCmd;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端「客户与会员」的优惠券接口：券模板（创建 / 分页 / 发放下拉）、发放、发放记录。
 * Controller 只读 Service 端口，不直接碰 mapper / PO（ArchUnit 规则 1）。
 *
 * <p>权限按语义拆两域（R15）：券模板的「分页 / 新建」要 {@code coupon:template:manage}，
 * 发券与发券下拉要 {@code customer:grant}。原先六个端点共用 {@code customer:grant}，
 * 等于「能授信客户的人可以自建券模板并全量发券」。</p>
 */
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    private final CouponQueryService couponQueryService;
    private final CouponIssueUseCase issueUseCase;
    private final CouponTemplateAdminUseCase templateAdminUseCase;

    public AdminCouponController(CouponQueryService couponQueryService,
                                 CouponIssueUseCase issueUseCase,
                                 CouponTemplateAdminUseCase templateAdminUseCase) {
        this.couponQueryService = couponQueryService;
        this.issueUseCase = issueUseCase;
        this.templateAdminUseCase = templateAdminUseCase;
    }

    /**
     * 发放下拉用：全部可用券档位（含自建券，软删的不出现）。
     * <p>归属发券流程（{@code customer:grant}），不并入券模板管理权限：
     * 有权发券的人需要看到可选券，但不等于有权新建/删除券模板。</p>
     */
    @GetMapping("/templates")
    @RequirePerm("customer:grant")
    public R<List<CouponDtos.TemplateVO>> templates() {
        return R.ok(couponQueryService.listTemplates());
    }

    /** 券模板分页列表：券号 / 券型 / 创建人 / 创建时间（券模板管理域）。 */
    @GetMapping("/templates/page")
    @RequirePerm("coupon:template:manage")
    public R<PageResult<CouponDtos.TemplatePageVO>> templatePage(
            @ModelAttribute PageQuery pageQuery,
            @RequestParam(required = false) String couponType,
            @RequestParam(required = false) String keyword) {
        return R.ok(couponQueryService.listTemplatePage(pageQuery, couponType, keyword));
    }

    /**
     * 新建券模板（可指定 SPU/SKU，实现「特定商品」的券）。
     * <p>独立权限点（R15）：原先复用 {@code customer:grant}，导致「能授信客户的人」顺带能造券并全量发券；
     * 现在建模板与发券彻底分开，权限矩阵可按岗授予。</p>
     */
    @PostMapping("/templates")
    @RequirePerm("coupon:template:manage")
    public R<CouponDtos.CreateTemplateResult> createTemplate(
            @Valid @RequestBody CreateCouponTemplateCmd cmd,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        long staffId = user == null ? 0L : user.userId();
        return R.ok(templateAdminUseCase.create(cmd, staffId));
    }

    @GetMapping("/issues")
    @RequirePerm("customer:grant")
    public R<List<CouponDtos.IssueVO>> issues(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(couponQueryService.listRecentIssues(limit));
    }

    /** 发放记录分页列表（带客户昵称与券状态）。 */
    @GetMapping("/issues/page")
    @RequirePerm("customer:grant")
    public R<PageResult<CouponDtos.IssuePageVO>> issuePage(
            @ModelAttribute PageQuery pageQuery,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword) {
        return R.ok(couponQueryService.listIssuePage(pageQuery, source, keyword));
    }

    /** customerIds 为空 = 全员发放；thresholdCent 不传 = 沿用模板门槛，传 0 = 无门槛。 */
    @PostMapping("/issue")
    @RequirePerm("customer:grant")
    public R<Integer> issue(@Valid @RequestBody IssueCmd cmd) {
        return R.ok(issueUseCase.issue(new IssueCouponCmd(cmd.templateId(), cmd.customerIds(),
                cmd.thresholdCent(), cmd.reason(), cmd.scene())));
    }

    public record IssueCmd(long templateId, List<Long> customerIds, Long thresholdCent,
                           String reason, String scene) {
    }
}
