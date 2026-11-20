package com.sent.sports.chat.controller.admin;

import com.sent.sports.chat.service.ChatAuditBatchDeleteUseCase;
import com.sent.sports.common.anno.Confirm;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客服审查列表批量软删：确认令牌与操作短语完全对齐发票管理。 */
@RestController
@RequestMapping("/api/admin/chat/audit")
public class AdminChatAuditBatchDeleteController {

    private final ChatAuditBatchDeleteUseCase deleteUseCase;

    public AdminChatAuditBatchDeleteController(ChatAuditBatchDeleteUseCase deleteUseCase) {
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/sessions/batch-delete")
    @Confirm(action = ConfirmAction.BATCH_DELETE)
    @RequirePerm(value = "chat:audit", exact = true)
    public R<BatchDeleteVO> batchDelete(
            @Valid @RequestBody BatchDeleteCmd cmd) {
        return R.ok(new BatchDeleteVO(deleteUseCase.delete(cmd.ids())));
    }
}
