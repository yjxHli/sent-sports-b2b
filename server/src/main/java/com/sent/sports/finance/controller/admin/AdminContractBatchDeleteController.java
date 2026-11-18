package com.sent.sports.finance.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.web.R;
import com.sent.sports.finance.service.ContractBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合同管理列表批量软删：按合同号，合同金额与到期日不改写。 */
@RestController
@RequestMapping("/api/admin/contracts")
public class AdminContractBatchDeleteController {

    private final ConfirmTokenService confirmTokenService;
    private final ContractBatchDeleteUseCase deleteUseCase;

    public AdminContractBatchDeleteController(ConfirmTokenService confirmTokenService,
                                              ContractBatchDeleteUseCase deleteUseCase) {
        this.confirmTokenService = confirmTokenService;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/batch-delete")
    @RequirePerm("finance:invoice")
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
