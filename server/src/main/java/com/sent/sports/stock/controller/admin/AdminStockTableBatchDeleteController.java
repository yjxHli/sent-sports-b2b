package com.sent.sports.stock.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import com.sent.sports.stock.service.StockFlowBatchDeleteUseCase;
import com.sent.sports.stock.service.StockWarningBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

/** 报损/入库流水 + 库存预警清单批量软删：库存数量与流水快照一概不动。 */
@RestController
@RequestMapping("/api/admin/stock")
public class AdminStockTableBatchDeleteController {

    private final StockFlowBatchDeleteUseCase flowDelete;
    private final StockWarningBatchDeleteUseCase warningDelete;

    public AdminStockTableBatchDeleteController(StockFlowBatchDeleteUseCase flowDelete,
                                                StockWarningBatchDeleteUseCase warningDelete) {
        this.flowDelete = flowDelete;
        this.warningDelete = warningDelete;
    }

    @PostMapping("/flows/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("stock:inbound")
    public R<BatchDeleteVO> deleteFlows(@Valid @RequestBody BatchDeleteCmd cmd) {
        return execute(cmd, flowDelete::delete);
    }

    @PostMapping("/warnings/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("system:report:view")
    public R<BatchDeleteVO> deleteWarnings(@Valid @RequestBody BatchDeleteCmd cmd) {
        return execute(cmd, warningDelete::delete);
    }

    private R<BatchDeleteVO> execute(BatchDeleteCmd cmd, Function<List<String>, Integer> delete) {
        return R.ok(new BatchDeleteVO(delete.apply(cmd.ids())));
    }
}
