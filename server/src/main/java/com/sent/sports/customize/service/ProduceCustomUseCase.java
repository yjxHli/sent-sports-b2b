package com.sent.sports.customize.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.domain.CustomAction;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.dto.ProduceCustomCmd;
import com.sent.sports.customize.service.dto.ProduceCustomResult;

import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 排产完工用例（P3-29）：plan §7.5 / 验收 A7——
 * APPROVED 经 START_PRODUCTION→IN_PRODUCTION（记 started_at）；IN_PRODUCTION 经 FINISH→FINISHED（记 finished_at）；
 * 非对应状态排产/完工均抛 STATE_CONFLICT；未知动作抛 PARAM_INVALID。
 * 验收 A7 的"已排产不可退款、完工才可发货"由退款/发货门面保障（见 CustomizeRefundGateway / CustomizeShipGateway）。
 * 仅依赖 customize 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class ProduceCustomUseCase {

    private final CustomizeRepository customizeRepository;
    private final Clock clock;

    public ProduceCustomUseCase(CustomizeRepository customizeRepository, Clock clock) {
        this.customizeRepository = customizeRepository;
        this.clock = clock;
    }

    @UseCase
    public ProduceCustomResult produce(ProduceCustomCmd cmd) {
        CustomOrder order = customizeRepository.findByCustomOrderId(cmd.customOrderId())
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "定制单不存在"));
        String action = cmd.action();

        if ("START_PRODUCTION".equals(action)) {
            if (order.getStatus() != CustomStatus.APPROVED) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "仅审核通过的定制单可排产");
            }
            long now = clock.instant().toEpochMilli();
            order.transit(CustomAction.START_PRODUCTION);
            order.setStartedAtMillis(now);
            customizeRepository.save(order);
            return new ProduceCustomResult(order.getCustomOrderId(), order.getStatus().name(),
                    order.getStartedAtMillis(), order.getFinishedAtMillis());
        } else if ("FINISH".equals(action)) {
            if (order.getStatus() != CustomStatus.IN_PRODUCTION) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "仅生产中的定制单可完工");
            }
            long now = clock.instant().toEpochMilli();
            order.transit(CustomAction.FINISH);
            order.setFinishedAtMillis(now);
            customizeRepository.save(order);
            return new ProduceCustomResult(order.getCustomOrderId(), order.getStatus().name(),
                    order.getStartedAtMillis(), order.getFinishedAtMillis());
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的排产动作");
    }
}
