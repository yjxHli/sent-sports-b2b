package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.InvoiceMapper;
import com.sent.sports.finance.mapper.po.InvoiceNotifyRow;
import org.springframework.stereotype.Service;

/**
 * 管理端开票申请处置：驳回 / 标记异常。
 * <p>两个动作都会改状态并投站内信；影响 0 行表示已被别人处理过，既不重复写状态也不重复发信。</p>
 */
@Service
public class InvoiceAuditUseCase {

    private final InvoiceMapper invoiceMapper;
    private final InvoiceNotificationService notificationService;

    public InvoiceAuditUseCase(InvoiceMapper invoiceMapper,
                               InvoiceNotificationService notificationService) {
        this.invoiceMapper = invoiceMapper;
        this.notificationService = notificationService;
    }

    /** 驳回开票申请：APPLIED / ISSUED → REJECTED。 */
    @UseCase
    public String markRejected(long invoiceId, String reason) {
        String trimmed = requireReason(reason);
        requireExisting(invoiceId);
        if (invoiceMapper.markRejected(invoiceId) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "发票申请已被处理，请刷新后重试");
        }
        notificationService.rejected(invoiceId, trimmed);
        return "REJECTED";
    }

    /** 标记开票异常 / 失败：任何非 EXCEPTION 状态 → EXCEPTION。 */
    @UseCase
    public String markException(long invoiceId, String reason) {
        String trimmed = requireReason(reason);
        requireExisting(invoiceId);
        if (invoiceMapper.markException(invoiceId) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该发票已标记为异常，请勿重复操作");
        }
        notificationService.exception(invoiceId, trimmed);
        return "EXCEPTION";
    }

    private void requireExisting(long invoiceId) {
        InvoiceNotifyRow row = invoiceMapper.selectNotifyRow(invoiceId);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "开票申请不存在");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "必须填写原因");
        }
        return reason.trim();
    }
}
