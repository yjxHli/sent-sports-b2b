package com.sent.sports.coupon.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import com.sent.sports.coupon.service.CouponIssueAdminUseCase;
import com.sent.sports.coupon.service.CouponTemplateAdminUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 券模板与发放记录的批量软删（2026-11-18 用户需求 2、3）。
 *
 * <p>与其余六个列表同一条危险操作链路：前端 {@code openConfirm({action:'BATCH_DELETE'})}
 * 换一次性令牌，后端由 {@code ConfirmTokenInterceptor} 用同一串归一化 ID 消费
 * （trim → 去空 → 去重 → 字典序），口径只此一处。</p>
 */
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponBatchDeleteController {

    private final CouponTemplateAdminUseCase templateUseCase;
    private final CouponIssueAdminUseCase issueUseCase;

    public AdminCouponBatchDeleteController(CouponTemplateAdminUseCase templateUseCase,
                                            CouponIssueAdminUseCase issueUseCase) {
        this.templateUseCase = templateUseCase;
        this.issueUseCase = issueUseCase;
    }

    /** 删除自建券模板：只打 deleted 标记，已发出的券快照不受影响。 */
    @PostMapping("/templates/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("coupon:template:manage")
    public R<BatchDeleteVO> deleteTemplates(@Valid @RequestBody BatchDeleteCmd cmd) {
        return R.ok(new BatchDeleteVO(templateUseCase.delete(cmd.ids())));
    }

    /** 删除发放记录：券从发放列表与客户「我的优惠券」消失，订单折扣额不变。 */
    @PostMapping("/issues/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("customer:grant")
    public R<BatchDeleteVO> deleteIssues(@Valid @RequestBody BatchDeleteCmd cmd) {
        return R.ok(new BatchDeleteVO(issueUseCase.delete(cmd.ids())));
    }
}
