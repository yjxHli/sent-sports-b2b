package com.sent.sports.customer.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import com.sent.sports.customer.service.CustomerBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户列表 / 授信企业客户批量软删。 */
@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerBatchDeleteController {

    private final CustomerBatchDeleteUseCase deleteUseCase;

    public AdminCustomerBatchDeleteController(CustomerBatchDeleteUseCase deleteUseCase) {
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("customer:manage")
    public R<BatchDeleteVO> batchDelete(
            @Valid @RequestBody BatchDeleteCmd cmd) {
        return R.ok(new BatchDeleteVO(deleteUseCase.delete(cmd.ids())));
    }
}
