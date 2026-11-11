package com.sent.sports.stock.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.stock.domain.StockFlow;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.repository.StockFlowRepository;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.dto.LossCmd;
import com.sent.sports.stock.service.dto.LossResult;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 报损用例（P3-33）：spec §8.3 / 验收——
 * 必须填原因（PARAM_INVALID）、不得超实物（StockItem.loss 抛 STOCK_NOT_ENOUGH）、写库存流水（LOSS）、可售同步减少。
 * 仅依赖 stock 模块内部仓储端口与 common 工具，符合 ArchUnit 规则 4。
 * 注：spec 提及报损可附凭证图片，凭证由 P4 接口层采集并随 LossCmd 透传，此处领域校验仅强制原因非空。
 */
@Service
public class LossUseCase {

    private final StockRepository stockRepository;
    private final StockFlowRepository stockFlowRepository;
    private final IdGen idGen;
    private final Clock clock;

    public LossUseCase(StockRepository stockRepository, StockFlowRepository stockFlowRepository,
                       IdGen idGen, Clock clock) {
        this.stockRepository = stockRepository;
        this.stockFlowRepository = stockFlowRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public LossResult loss(LossCmd cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        StockItem item = stockRepository.findBySku(cmd.skuId())
                .orElseGet(() -> new StockItem(0L, 0L, 0L, 0L));
        long beforeQty = item.getPhysicalQty();
        item.loss(cmd.qty()); // 领域层校验数量>0（PARAM_INVALID）与不得超实物（STOCK_NOT_ENOUGH）
        long now = clock.instant().toEpochMilli();
        long afterQty = item.getPhysicalQty();

        stockRepository.save(cmd.skuId(), item);
        stockFlowRepository.save(new StockFlow(idGen.next("SF"), cmd.skuId(), "LOSS",
                cmd.qty(), beforeQty, afterQty, idGen.next("LS"), cmd.operatorId(), cmd.remark(), now));

        return new LossResult(cmd.skuId(), afterQty, item.availableQty());
    }
}
