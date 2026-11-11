package com.sent.sports.stock.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.stock.domain.StockCheck;
import com.sent.sports.stock.domain.StockFlow;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.repository.StockCheckRepository;
import com.sent.sports.stock.repository.StockFlowRepository;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.dto.ApproveCheckCmd;
import com.sent.sports.stock.service.dto.ApproveCheckResult;
import com.sent.sports.stock.service.dto.CreateCheckCmd;
import com.sent.sports.stock.service.dto.CreateCheckResult;
import com.sent.sports.stock.service.dto.RecordCheckCmd;
import com.sent.sports.stock.service.dto.RecordCheckResult;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 盘点用例（P3-35）：spec §8.3 / 验收——
 * 创建盘点单（取账面实物量）、录入实盘数生成差异（record）、审批后按差异调账并写 CHECK 流水。
 * 确认令牌由 P4 接口层校验，本用例仅负责状态机与库存调账。
 * 仅依赖 stock 模块内部仓储端口与 common 工具，符合 ArchUnit 规则 4。
 */
@Service
public class StockCheckUseCase {

    private final StockRepository stockRepository;
    private final StockCheckRepository stockCheckRepository;
    private final StockFlowRepository stockFlowRepository;
    private final IdGen idGen;
    private final Clock clock;

    public StockCheckUseCase(StockRepository stockRepository, StockCheckRepository stockCheckRepository,
                             StockFlowRepository stockFlowRepository, IdGen idGen, Clock clock) {
        this.stockRepository = stockRepository;
        this.stockCheckRepository = stockCheckRepository;
        this.stockFlowRepository = stockFlowRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public CreateCheckResult create(CreateCheckCmd cmd) {
        StockItem item = stockRepository.findBySku(cmd.skuId())
                .orElseGet(() -> new StockItem(0L, 0L, 0L, 0L));
        long now = clock.instant().toEpochMilli();
        StockCheck check = new StockCheck(idGen.next("SC"), cmd.skuId(), item.getPhysicalQty(), cmd.operatorId(), now);
        stockCheckRepository.save(check);
        return new CreateCheckResult(check.getCheckNo(), check.getSkuId(), check.getBookQty(), check.getStatus());
    }

    @UseCase
    public RecordCheckResult record(RecordCheckCmd cmd) {
        StockCheck check = stockCheckRepository.findByCheckId(cmd.checkNo())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (!StockCheck.DRAFT.equals(check.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        long now = clock.instant().toEpochMilli();
        check.record(cmd.actualQty(), now);
        stockCheckRepository.save(check);
        return new RecordCheckResult(check.getCheckNo(), check.getBookQty(), check.getActualQty(),
                check.getDiffQty(), check.getStatus());
    }

    @UseCase
    public ApproveCheckResult approve(ApproveCheckCmd cmd) {
        StockCheck check = stockCheckRepository.findByCheckId(cmd.checkNo())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (!StockCheck.COUNTED.equals(check.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        long now = clock.instant().toEpochMilli();
        long diff = check.getDiffQty();
        StockItem item = stockRepository.findBySku(check.getSkuId())
                .orElseGet(() -> new StockItem(0L, 0L, 0L, 0L));
        long beforeQty = item.getPhysicalQty();
        if (diff > 0) {
            item.inbound(diff); // 实盘多于账面：补入
        } else if (diff < 0) {
            item.loss(-diff);   // 实盘少于账面：报损出库
        }
        long afterQty = item.getPhysicalQty();
        stockRepository.save(check.getSkuId(), item);
        stockFlowRepository.save(new StockFlow(idGen.next("SF"), check.getSkuId(), "CHECK",
                diff, beforeQty, afterQty, check.getCheckNo(), cmd.operatorId(), "盘点调账", now));
        check.approve(now);
        stockCheckRepository.save(check);
        return new ApproveCheckResult(check.getCheckNo(), check.getStatus(), afterQty);
    }
}
