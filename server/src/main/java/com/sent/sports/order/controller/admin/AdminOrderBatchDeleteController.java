package com.sent.sports.order.controller.admin;

import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import com.sent.sports.order.service.OrderBatchDeleteUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单列表批量软删：独立控制器，只写 admin_deleted_at。
 * 权限沿用订单关闭（order:close），不新增角色矩阵配置。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderBatchDeleteController {

    private final OrderBatchDeleteUseCase deleteUseCase;

    public AdminOrderBatchDeleteController(OrderBatchDeleteUseCase deleteUseCase) {
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm("order:close")
    public R<BatchDeleteVO> batchDelete(
            @Valid @RequestBody BatchDeleteCmd cmd) {
        return R.ok(new BatchDeleteVO(deleteUseCase.delete(cmd.ids())));
    }
}
