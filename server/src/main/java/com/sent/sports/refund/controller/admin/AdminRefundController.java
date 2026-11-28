package com.sent.sports.refund.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.refund.dto.RefundDtos;
import com.sent.sports.refund.service.RefundAuditUseCase;
import com.sent.sports.refund.service.RefundQueryService;
import com.sent.sports.refund.service.dto.AuditRefundCmd;
import com.sent.sports.refund.service.dto.AuditRefundResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端退款审核（收口阶段）：{@code GET /api/admin/refunds} 真实分页、
 * {@code GET .../{no}} 单笔审核详情、{@code POST .../{no}/audit} 审核。
 * <p>列表联查订单号与申请原因；详情一次给出退款单 + 订单基本信息 + 客户 + 商品行，
 * 支撑列表行点「详情」进入的独立审核页；审核复用 P3 的 {@link RefundAuditUseCase}
 * （APPROVE / REJECT），前端通过时带 {@code X-Confirm-Token}，驳回必须填写原因，由用例二次校验。</p>
 */
@RestController
@RequestMapping("/api/admin/refunds")
public class AdminRefundController {

    private final RefundAuditUseCase refundAuditUseCase;
    private final RefundQueryService refundQueryService;

    public AdminRefundController(RefundAuditUseCase refundAuditUseCase,
                                 RefundQueryService refundQueryService) {
        this.refundAuditUseCase = refundAuditUseCase;
        this.refundQueryService = refundQueryService;
    }

    /** 审核列表：status 为空时返回全部状态，按申请时间倒序；keyword 同时匹配退款单号与订单号。 */
    @GetMapping
    @RequirePerm("refund:view")
    public R<PageResult<RefundDtos.RefundAuditVO>> list(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(refundQueryService.listAuditRefunds(page, status, keyword));
    }

    /**
     * 审核详情（单笔）：退款单 + 订单基本信息 + 客户 + 商品明细 + 物流。
     * <p>列表行点「详情」进入独立审核页，由本端点一次喂全数据；
     * 退款单不存在走 NOT_FOUND，响应里不会出现只有 refundNo 的空壳。</p>
     */
    @GetMapping("/{refundNo}")
    @RequirePerm("refund:view")
    public R<RefundDtos.RefundAuditDetailVO> detail(@PathVariable String refundNo) {
        return R.ok(refundQueryService.getAuditDetail(refundNo));
    }

    /** 审核动作：通过需 {@code X-Confirm-Token}，驳回必须填写原因（用例二次校验）。 */
    @PostMapping("/{refundNo}/audit")
    @RequirePerm("refund:audit")
    public R<RefundDtos.RefundVO> audit(@PathVariable String refundNo,
                                        @Valid @RequestBody RefundDtos.AuditCmd cmd) {
        String action = Boolean.TRUE.equals(cmd.approved()) ? "APPROVE" : "REJECT";
        AuditRefundResult r = refundAuditUseCase.audit(
                new AuditRefundCmd(refundNo, action, cmd.remark()));
        return R.ok(new RefundDtos.RefundVO(r.refundNo(), null, r.status(), null, null));
    }
}
