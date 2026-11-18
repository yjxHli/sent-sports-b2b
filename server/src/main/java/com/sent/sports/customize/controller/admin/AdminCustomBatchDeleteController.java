package com.sent.sports.customize.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.web.R;
import com.sent.sports.customize.service.CustomBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 定制列表批量软删：按定制单号，图稿与审图日志保留可追溯。 */
@RestController
@RequestMapping("/api/admin/custom-orders")
public class AdminCustomBatchDeleteController {

    private final ConfirmTokenService confirmTokenService;
    private final CustomBatchDeleteUseCase deleteUseCase;

    public AdminCustomBatchDeleteController(ConfirmTokenService confirmTokenService,
                                            CustomBatchDeleteUseCase deleteUseCase) {
        this.confirmTokenService = confirmTokenService;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/batch-delete")
    @RequirePerm("custom:review")
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
