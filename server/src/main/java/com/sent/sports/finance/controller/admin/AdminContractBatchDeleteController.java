package com.sent.sports.finance.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import com.sent.sports.finance.service.ContractBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合同管理列表批量软删：按合同号，合同金额与到期日不改写。 */
@RestController
@RequestMapping("/api/admin/contracts")
public class AdminContractBatchDeleteController {

    private final ContractBatchDeleteUseCase deleteUseCase;

    public AdminContractBatchDeleteController(ContractBatchDeleteUseCase deleteUseCase) {
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("finance:invoice")
    public R<BatchDeleteVO> batchDelete(
            @Valid @RequestBody BatchDeleteCmd cmd) {
        return R.ok(new BatchDeleteVO(deleteUseCase.delete(cmd.ids())));
    }
}
