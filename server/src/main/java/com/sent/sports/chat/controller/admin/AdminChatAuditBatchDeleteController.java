package com.sent.sports.chat.controller.admin;

import com.sent.sports.chat.service.ChatAuditBatchDeleteUseCase;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.domain.ConfirmAction;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteCmd;
import com.sent.sports.common.dto.admin.BatchDeleteDtos.BatchDeleteVO;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客服审查列表批量软删：确认令牌与操作短语完全对齐发票管理。 */
@RestController
@RequestMapping("/api/admin/chat/audit")
public class AdminChatAuditBatchDeleteController {

    private final ConfirmTokenService confirmTokenService;
    private final ChatAuditBatchDeleteUseCase deleteUseCase;

    public AdminChatAuditBatchDeleteController(ConfirmTokenService confirmTokenService,
                                               ChatAuditBatchDeleteUseCase deleteUseCase) {
        this.confirmTokenService = confirmTokenService;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/sessions/batch-delete")
    @RequirePerm(value = "chat:audit", exact = true)
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
