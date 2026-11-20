package com.sent.sports.finance.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import com.sent.sports.finance.service.PaymentRecordBatchDeleteUseCase;
import com.sent.sports.finance.service.ReceivableBatchDeleteUseCase;
import com.sent.sports.finance.service.WriteOffBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

/** 应收台账 / 回款记录 / 核销冲销明细批量软删：金额与流水一律不改写。 */
@RestController
@RequestMapping("/api/admin")
public class AdminFinanceTableBatchDeleteController {

    private final ReceivableBatchDeleteUseCase receivableDelete;
    private final PaymentRecordBatchDeleteUseCase paymentDelete;
    private final WriteOffBatchDeleteUseCase writeOffDelete;

    public AdminFinanceTableBatchDeleteController(ReceivableBatchDeleteUseCase receivableDelete,
                                                  PaymentRecordBatchDeleteUseCase paymentDelete,
                                                  WriteOffBatchDeleteUseCase writeOffDelete) {
        this.receivableDelete = receivableDelete;
        this.paymentDelete = paymentDelete;
        this.writeOffDelete = writeOffDelete;
    }

    @PostMapping("/receivables/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("finance:record")
    public R<BatchDeleteVO> deleteReceivables(@Valid @RequestBody BatchDeleteCmd cmd) {
        return execute(cmd, receivableDelete::delete);
    }

    @PostMapping("/payment-records/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("finance:record")
    public R<BatchDeleteVO> deletePaymentRecords(@Valid @RequestBody BatchDeleteCmd cmd) {
        return execute(cmd, paymentDelete::delete);
    }

    @PostMapping("/write-offs/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("finance:writeoff")
    public R<BatchDeleteVO> deleteWriteOffs(@Valid @RequestBody BatchDeleteCmd cmd) {
        return execute(cmd, writeOffDelete::delete);
    }

    private R<BatchDeleteVO> execute(BatchDeleteCmd cmd, Function<List<String>, Integer> delete) {
        return R.ok(new BatchDeleteVO(delete.apply(cmd.ids())));
    }
}
