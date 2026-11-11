package com.sent.sports.stock.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.stock.domain.StockFlow;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.event.StockInboundEvent;
import com.sent.sports.stock.repository.StockFlowRepository;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.dto.InboundCmd;
import com.sent.sports.stock.service.dto.InboundResult;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 手工入库用例（P3-31）：spec §8.3 / 验收 A19——
 * 数量>0（领域层校验）、库存实物增加、写库存流水（INBOUND）、触发到货提醒事件（StockInboundEvent）。
 * 仅依赖 stock 模块内部仓储端口、event 与 common 工具，符合 ArchUnit 规则 4。
 */
@Service
public class InboundUseCase {

    private final StockRepository stockRepository;
    private final StockFlowRepository stockFlowRepository;
    private final DomainEventPublisher eventPublisher;
    private final IdGen idGen;
    private final Clock clock;

    public InboundUseCase(StockRepository stockRepository, StockFlowRepository stockFlowRepository,
                          DomainEventPublisher eventPublisher, IdGen idGen, Clock clock) {
        this.stockRepository = stockRepository;
        this.stockFlowRepository = stockFlowRepository;
        this.eventPublisher = eventPublisher;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public InboundResult inbound(InboundCmd cmd) {
        StockItem item = stockRepository.findBySku(cmd.skuId())
                .orElseGet(() -> new StockItem(0L, 0L, 0L, 0L));
        long beforeQty = item.getPhysicalQty();
        item.inbound(cmd.qty()); // 领域层校验数量>0（PARAM_INVALID）
        long now = clock.instant().toEpochMilli();
        long afterQty = item.getPhysicalQty();

        stockRepository.save(cmd.skuId(), item);
        stockFlowRepository.save(new StockFlow(idGen.next("SF"), cmd.skuId(), "INBOUND",
                cmd.qty(), beforeQty, afterQty, idGen.next("IB"), cmd.operatorId(), cmd.remark(), now));
        eventPublisher.publish(new StockInboundEvent(cmd.skuId(), cmd.qty(), afterQty));

        return new InboundResult(cmd.skuId(), afterQty, item.availableQty());
    }
}
