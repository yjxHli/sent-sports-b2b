package com.sent.sports.stock.repository;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.mapper.StockFlowMapper;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.LockReleaseLinePO;
import com.sent.sports.stock.mapper.po.SkuStockPO;
import com.sent.sports.stock.mapper.po.StockFlowPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 库存仓储实现（P5-20）：把「原子扣减」下沉给 SQL，Java 侧只做结果判定；每次数量变动都落一条流水。
 *
 * <h3>防重口径（刻意保持轻量）</h3>
 * <ul>
 *   <li><b>预占</b>：{@code lockStock} 用条件更新判断可用量，命中 0 行即可用不足，抛 {@code STOCK_NOT_ENOUGH(40910)}；
 *       并发下单不会超卖，因此不需要分布式锁。</li>
 *   <li><b>整单释放</b>：先插 {@code RELEASE} 流水，{@code uk_flow_biz(biz_type, biz_no, sku_id)} 命中重复即视为
 *       「这单已经释放过」，直接跳过——<b>靠数据库唯一索引实现幂等</b>，退款回调重试不会重复释放。</li>
 *   <li><b>台账调整</b>：以领域对象绝对值覆盖，并按差值补一条 {@code ADJUST} 流水，保证「台账 = 流水累计」可对账。</li>
 * </ul>
 */
@Repository
public class StockRepositoryImpl implements StockRepository {

    /** 流水变动类型（与 plan §7.2 对齐）。 */
    private static final String FLOW_LOCK = "LOCK";
    private static final String FLOW_RELEASE = "RELEASE";
    private static final String FLOW_ADJUST = "ADJUST";

    private final StockMapper stockMapper;
    private final StockFlowMapper stockFlowMapper;

    public StockRepositoryImpl(StockMapper stockMapper, StockFlowMapper stockFlowMapper) {
        this.stockMapper = stockMapper;
        this.stockFlowMapper = stockFlowMapper;
    }

    @Override
    public void lock(long skuId, int qty) {
        Integer before = lockedQtyOf(skuId);
        if (stockMapper.lockStock(skuId, qty) == 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        recordFlow(skuId, FLOW_LOCK, qty, before, before + qty, null);
    }

    @Override
    public void release(long skuId, int qty) {
        Integer before = lockedQtyOf(skuId);
        if (stockMapper.releaseStock(skuId, qty) == 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        recordFlow(skuId, FLOW_RELEASE, -qty, before, Math.max(0, before - qty), null);
    }

    @Override
    public void releaseByOrderNo(String orderNo) {
        for (LockReleaseLinePO line : stockMapper.selectLockedLinesByOrderNo(orderNo)) {
            int qty = line.getQty() == null ? 0 : line.getQty();
            if (qty <= 0) {
                continue;
            }
            SkuStockPO row = stockMapper.selectBySkuId(line.getSkuId());
            if (row == null) {
                // 该 SKU 从未建台账，说明当初也没有预占过，无需释放
                continue;
            }
            Integer before = row.getLockedQty();
            StockFlowPO flow = newFlow(line.getSkuId(), FLOW_RELEASE, -qty, before,
                    Math.max(0, before - qty), orderNo);
            try {
                stockFlowMapper.insert(flow);
            } catch (DuplicateKeyException alreadyReleased) {
                // uk_flow_biz 命中：本单该 SKU 已释放过，跳过（轻量幂等）
                continue;
            }
            if (stockMapper.releaseStock(line.getSkuId(), qty) == 0) {
                // 台账行缺失属于异常数据，撤销刚写的流水以保持「流水 = 台账变动」
                stockFlowMapper.deleteById(flow.getId());
            }
        }
    }

    @Override
    public Optional<StockItem> findBySku(long skuId) {
        SkuStockPO po = stockMapper.selectBySkuId(skuId);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(new StockItem(po.getPhysicalQty(), po.getLockedQty(),
                po.getPendingQty(), po.getFrozenQty()));
    }

    @Override
    public void save(long skuId, StockItem item) {
        SkuStockPO before = stockMapper.selectBySkuId(skuId);
        if (before == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "库存台账不存在，请先初始化 SKU 库存");
        }
        stockMapper.updateLedger(skuId, item.getPhysicalQty(), item.getLockedQty(),
                item.getPendingQty(), item.getFrozenQty());
        int delta = (int) (item.getPhysicalQty() - before.getPhysicalQty());
        if (delta != 0) {
            recordFlow(skuId, FLOW_ADJUST, delta, before.getPhysicalQty(),
                    (int) item.getPhysicalQty(), null);
        }
    }

    private Integer lockedQtyOf(long skuId) {
        SkuStockPO po = stockMapper.selectBySkuId(skuId);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "库存台账不存在，请先初始化 SKU 库存");
        }
        return po.getLockedQty();
    }

    private void recordFlow(long skuId, String bizType, int changeQty, int beforeQty, int afterQty, String bizNo) {
        stockFlowMapper.insert(newFlow(skuId, bizType, changeQty, beforeQty, afterQty, bizNo));
    }

    private StockFlowPO newFlow(long skuId, String bizType, int changeQty, int beforeQty, int afterQty, String bizNo) {
        StockFlowPO flow = new StockFlowPO();
        flow.setSkuId(skuId);
        flow.setBizType(bizType);
        flow.setChangeQty(changeQty);
        flow.setBeforeQty(beforeQty);
        flow.setAfterQty(afterQty);
        flow.setBizNo(bizNo);
        return flow;
    }
}
