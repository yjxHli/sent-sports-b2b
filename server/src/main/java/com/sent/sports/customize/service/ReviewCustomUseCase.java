package com.sent.sports.customize.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.domain.CustomAction;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomReviewLog;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.dto.ReviewCustomCmd;
import com.sent.sports.customize.service.dto.ReviewCustomResult;

import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 审核定制单用例（P3-27）：spec §6.5.3 / A5/A6——
 * 仅 WAIT_REVIEW 可审核；PASS 迁移 APPROVED 并锁定当前图稿版本（后续不可替换）；
 * REJECT 须填原因分类+说明，迁移 REJECTED 且驳回次数+1（累计 ≥3 时由提交侧经状态机升级 ESCALATED，客服不可再审）；
 * 评审时按 review_deadline 计算 24h 审核时限剩余毫秒（负值即超时）。
 * 仅依赖 customize 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class ReviewCustomUseCase {

    private final CustomizeRepository customizeRepository;
    private final Clock clock;

    public ReviewCustomUseCase(CustomizeRepository customizeRepository, Clock clock) {
        this.customizeRepository = customizeRepository;
        this.clock = clock;
    }

    @UseCase
    public ReviewCustomResult review(ReviewCustomCmd cmd) {
        CustomOrder order = customizeRepository.findByCustomOrderId(cmd.customOrderId())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "定制单不存在"));
        if (order.getStatus() != CustomStatus.WAIT_REVIEW) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅待审核定制单可审核");
        }

        String action = cmd.action();

        if ("PASS".equals(action)) {
            long now = clock.instant().toEpochMilli();
            long remainingMillis = order.getReviewDeadlineMillis() - now;
            int reviewedVersion = order.getCurrentVersion();
            order.transit(CustomAction.PASS);
            order.setLockedVersion(order.getCurrentVersion());
            customizeRepository.save(order);
            writeAuditLog(cmd, order, "PASS", reviewedVersion, now);
            return new ReviewCustomResult(order.getCustomOrderId(), order.getStatus().name(),
                    order.getLockedVersion(), order.getReviewDeadlineMillis(), remainingMillis);
        } else if ("REJECT".equals(action)) {
            if (cmd.reasonCode() == null || cmd.reasonCode().isBlank()
                    || cmd.remark() == null || cmd.remark().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须选择原因分类并填写说明");
            }
            long now = clock.instant().toEpochMilli();
            long remainingMillis = order.getReviewDeadlineMillis() - now;
            int reviewedVersion = order.getCurrentVersion();
            order.transit(CustomAction.REJECT);
            order.incrementRejectCount();
            customizeRepository.save(order);
            writeAuditLog(cmd, order, "REJECT", reviewedVersion, now);
            return new ReviewCustomResult(order.getCustomOrderId(), order.getStatus().name(),
                    order.getLockedVersion(), order.getReviewDeadlineMillis(), remainingMillis);
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的审核动作");
    }

    /**
     * 审图日志真库落账：一次审核一条流水，版本号取「审核动作发生前」的当前图稿版本，
     * 由 V13 唯一键 uk_review_once 兜底重复审核（INSERT IGNORE 天然幂等，无分布式锁）。
     */
    private void writeAuditLog(ReviewCustomCmd cmd, CustomOrder order, String result,
                               int draftVersion, long now) {
        customizeRepository.insertReviewLog(new CustomReviewLog(
                order.getCustomOrderId(), draftVersion, result,
                cmd.reasonCode(), cmd.remark(), cmd.reviewerId(), now));
    }
}
