package com.sent.sports.coupon.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.web.R;
import com.sent.sports.coupon.service.CouponIssueAdminUseCase;
import com.sent.sports.coupon.service.CouponTemplateAdminUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 券模板与发放记录的批量软删（2026-11-18 用户需求 2、3）。
 *
 * <p>与其余六个列表同一条危险操作链路：前端 {@code openConfirm({action:'BATCH_DELETE'})}
 * 换一次性令牌，后端用同一串归一化 ID 消费。targetId 两侧必须同口径
 * （trim → 去空 → 去重 → 字典序），否则会误报 40302。</p>
 */
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponBatchDeleteController {

    private final ConfirmTokenService confirmTokenService;
    private final CouponTemplateAdminUseCase templateUseCase;
    private final CouponIssueAdminUseCase issueUseCase;

    public AdminCouponBatchDeleteController(ConfirmTokenService confirmTokenService,
                                            CouponTemplateAdminUseCase templateUseCase,
                                            CouponIssueAdminUseCase issueUseCase) {
        this.confirmTokenService = confirmTokenService;
        this.templateUseCase = templateUseCase;
        this.issueUseCase = issueUseCase;
    }

    /** 删除自建券模板：只打 deleted 标记，已发出的券快照不受影响。 */
    @PostMapping("/templates/batch-delete")
    @RequirePerm("customer:grant")
    public R<BatchDeleteVO> deleteTemplates(
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken,
            @Valid @RequestBody BatchDeleteCmd cmd) {
        assertConfirmToken(confirmToken, cmd);
        return R.ok(new BatchDeleteVO(templateUseCase.delete(cmd.ids())));
    }

    /** 删除发放记录：券从发放列表与客户「我的优惠券」消失，订单折扣额不变。 */
    @PostMapping("/issues/batch-delete")
    @RequirePerm("customer:grant")
    public R<BatchDeleteVO> deleteIssues(
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken,
            @Valid @RequestBody BatchDeleteCmd cmd) {
        assertConfirmToken(confirmToken, cmd);
        return R.ok(new BatchDeleteVO(issueUseCase.delete(cmd.ids())));
    }

    private void assertConfirmToken(String confirmToken, BatchDeleteCmd cmd) {
        String targetId = BatchIds.targetId(cmd.ids());
        if (confirmToken == null || confirmToken.isBlank()
                || !confirmTokenService.consume(ConfirmAction.BATCH_DELETE.name(), targetId, confirmToken)) {
            throw new BizException(ErrorCode.CONFIRM_TOKEN_INVALID);
        }
    }
}
