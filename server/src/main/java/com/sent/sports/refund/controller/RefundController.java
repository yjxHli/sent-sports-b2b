package com.sent.sports.refund.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.service.RefundApplyUseCase;
import com.sent.sports.refund.service.RefundAuditUseCase;
import com.sent.sports.refund.service.RefundQueryService;
import com.sent.sports.refund.service.dto.AuditRefundCmd;
import com.sent.sports.refund.service.dto.RefundApplyCmd;
import com.sent.sports.refund.service.dto.RefundApplyLineCmd;
import com.sent.sports.refund.service.dto.RefundApplyResult;
import com.sent.sports.refund.service.dto.AuditRefundResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 退款控制器（P4-22）：申请退款 / 我的售后 / 详情 / 申诉（plan §8.6）。
 * <p>依赖同模块用例（{@link RefundApplyUseCase} / {@link RefundAuditUseCase}）与查询门面
 * {@link RefundQueryService}。用户身份由 {@link CurrentUser} 请求属性注入（aud=user），
 * 列表 / 详情据此过滤归属，非本人退款单详情返回 NOT_FOUND。</p>
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundApplyUseCase refundApplyUseCase;
    private final RefundAuditUseCase refundAuditUseCase;
    private final RefundQueryService refundQueryService;

    public RefundController(RefundApplyUseCase refundApplyUseCase,
                            RefundAuditUseCase refundAuditUseCase,
                            RefundQueryService refundQueryService) {
        this.refundApplyUseCase = refundApplyUseCase;
        this.refundAuditUseCase = refundAuditUseCase;
        this.refundQueryService = refundQueryService;
    }

    @PostMapping
    @RequirePerm("refund:apply")
    public R<RefundDtos.RefundVO> apply(@Valid @RequestBody RefundDtos.ApplyRefundCmd cmd) {
        RefundApplyResult r = refundApplyUseCase.apply(toCmd(cmd));
        return R.ok(toVo(r));
    }

    @GetMapping
    public R<PageResult<RefundDtos.RefundVO>> list(@ModelAttribute PageQuery page,
                                                  @RequestParam(required = false) String status,
                                                  @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        requireUser(user);
        return R.ok(refundQueryService.listMyRefunds(page, user.userId(), status));
    }

    @GetMapping("/{refundNo}")
    public R<RefundDtos.RefundDetailVO> detail(@PathVariable String refundNo,
                                              @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        requireUser(user);
        return R.ok(refundQueryService.getDetail(refundNo, user.userId()));
    }

    @PostMapping("/{refundNo}/appeal")
    @RequirePerm("refund:appeal")
    public R<RefundDtos.RefundVO> appeal(@PathVariable String refundNo) {
        AuditRefundResult r = refundAuditUseCase.audit(new AuditRefundCmd(refundNo, "APPEAL", null));
        return R.ok(toVo(r));
    }

    private void requireUser(CurrentUser user) {
        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private RefundApplyCmd toCmd(RefundDtos.ApplyRefundCmd cmd) {
        List<RefundApplyLineCmd> lines = cmd.lines().stream()
                .map(l -> new RefundApplyLineCmd(l.skuId(), l.amountCent())).toList();
        return new RefundApplyCmd(cmd.orderNo(), lines, cmd.reason(), cmd.proof());
    }

    private RefundDtos.RefundVO toVo(RefundApplyResult r) {
        return new RefundDtos.RefundVO(r.refundNo(), r.orderNo(), r.status(),
                r.amountCent(), MoneyUtil.toYuan(r.amountCent()));
    }

    private RefundDtos.RefundVO toVo(AuditRefundResult r) {
        return new RefundDtos.RefundVO(r.refundNo(), null, r.status(), null, null);
    }
}
