package com.sent.sports.refund.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.web.R;
import com.sent.sports.refund.service.RefundBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 退款审核列表批量软删：独立控制器，避免给既有审核控制器堆构造参数。 */
@RestController
@RequestMapping("/api/admin/refunds")
public class AdminRefundBatchDeleteController {

    private final ConfirmTokenService confirmTokenService;
    private final RefundBatchDeleteUseCase deleteUseCase;

    public AdminRefundBatchDeleteController(ConfirmTokenService confirmTokenService,
                                            RefundBatchDeleteUseCase deleteUseCase) {
        this.confirmTokenService = confirmTokenService;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/batch-delete")
    @RequirePerm("refund:audit")
    public R<BatchDeleteVO> batchDelete(
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken,
            @Valid @RequestBody BatchDeleteCmd cmd) {
        String targetId = BatchIds.targetId(cmd.ids());
        if (confirmToken == null || confirmToken.isBlank()
                || !confirmTokenService.consume(ConfirmAction.BATCH_DELETE.name(), targetId, confirmToken)) {
            throw new BizException(ErrorCode.CONFIRM_TOKEN_INVALID);
        }
        return R.ok(new BatchDeleteVO(deleteUseCase.delete(cmd.ids())));
    }
}
