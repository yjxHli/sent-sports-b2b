package com.sent.sports.customize.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customize.domain.CustomAction;
import com.sent.sports.customize.domain.CustomDraft;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.dto.SubmitDraftCmd;
import com.sent.sports.customize.service.dto.SubmitDraftResult;

import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 提交图稿用例（P3-25）：plan §7 / spec §6.5.3——版本号 +1，文件已上传，必须有附件，
 * 单文件超 50MB 拒绝；写 custom_draft（只 insert），置 review_deadline = now + 24h；
 * 状态由 WAIT_DRAFT→WAIT_REVIEW（首次）或 REJECTED→WAIT_REVIEW（重传，驳回累计 ≥3 时升级 ESCALATED）。
 * 仅依赖 customize 模块内部仓储端口与 common 工具，符合 ArchUnit 规则 4。
 */
@Service
public class SubmitDraftUseCase {

    /** 单文件大小上限 50MB（对齐 spec §6.5.3）。 */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    /** 审核时限 24 小时（plan §7）。 */
    private static final long REVIEW_TIMEOUT_MILLIS = 24L * 3600 * 1000;

    private final CustomizeRepository customizeRepository;
    private final IdGen idGen;
    private final Clock clock;

    public SubmitDraftUseCase(CustomizeRepository customizeRepository, IdGen idGen, Clock clock) {
        this.customizeRepository = customizeRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public SubmitDraftResult submit(SubmitDraftCmd cmd) {
        CustomOrder order = customizeRepository.findByCustomOrderId(cmd.customOrderId())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "定制单不存在"));

        // 必须有附件：文件标识为空或大小为 0 视为无图稿（spec §6.5.3 无图稿不得提交）
        if (cmd.fileId() == null || cmd.fileId().isBlank() || cmd.fileSize() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "必须上传图稿附件");
        }
        // 单文件超 50MB 拒绝
        if (cmd.fileSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图稿单文件不得超过 50MB");
        }

        // 仅 待提交图稿 / 审核驳回 可提交
        CustomAction action;
        if (order.getStatus() == CustomStatus.WAIT_DRAFT) {
            action = CustomAction.SUBMIT_DRAFT;
        } else if (order.getStatus() == CustomStatus.REJECTED) {
            action = CustomAction.RESUBMIT;
        } else {
            throw new BizException(ErrorCode.STATE_CONFLICT, "当前定制单状态不可提交图稿");
        }

        long now = clock.instant().toEpochMilli();
        order.bumpVersion();
        order.transit(action);
        order.setReviewDeadlineMillis(now + REVIEW_TIMEOUT_MILLIS);

        CustomDraft draft = new CustomDraft(idGen.next("DR"), order.getCustomOrderId(),
                order.getCurrentVersion(), cmd.fileId(), cmd.fileName(), cmd.fileSize(), now);
        customizeRepository.insertDraft(draft);
        customizeRepository.save(order);

        return new SubmitDraftResult(order.getCustomOrderId(), order.getCurrentVersion(),
                order.getStatus().name(), order.getReviewDeadlineMillis());
    }
}
