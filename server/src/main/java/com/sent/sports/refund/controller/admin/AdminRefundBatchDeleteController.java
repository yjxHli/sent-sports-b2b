package com.sent.sports.refund.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import com.sent.sports.refund.service.RefundBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 退款审核列表批量软删：独立控制器，避免给既有审核控制器堆构造参数。 */
@RestController
@RequestMapping("/api/admin/refunds")
public class AdminRefundBatchDeleteController {

    private final RefundBatchDeleteUseCase deleteUseCase;

    public AdminRefundBatchDeleteController(RefundBatchDeleteUseCase deleteUseCase) {
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("refund:audit")
    public R<BatchDeleteVO> batchDelete(
            @Valid @RequestBody BatchDeleteCmd cmd) {
        return R.ok(new BatchDeleteVO(deleteUseCase.delete(cmd.ids())));
    }
}
