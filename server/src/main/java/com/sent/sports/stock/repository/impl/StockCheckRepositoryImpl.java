package com.sent.sports.stock.repository.impl;

import com.sent.sports.common.util.TimeUtil;
import com.sent.sports.stock.domain.StockCheck;
import com.sent.sports.stock.mapper.StockCheckMapper;
import com.sent.sports.stock.mapper.po.StockCheckItemPO;
import com.sent.sports.stock.mapper.po.StockCheckPO;
import com.sent.sports.stock.repository.StockCheckRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 盘点单仓储（真库，2026-11-28 P0 修复）。
 *
 * <p>替换原先的 {@code InMemoryStockCheckRepository}：盘点单原来只存在进程内的
 * {@code ConcurrentHashMap} 里，应用重启即全部丢失，多实例部署时各实例还会看到不同的盘点单。
 * 现在落 {@code stock_check}（头）+ {@code stock_check_item}（行）两张 V5 就建好、但一直空置的表。
 *
 * <p>两个取舍写在明面上：
 * <ol>
 *   <li>头表没有 {@code recorded_at / approved_at} 列，录实盘与审批的时点用 {@code updated_at} 近似
 *       —— 每次 save 都会刷新 {@code updated_at}，所以在「录完实盘、还没审批」这个窗口里读到的就是录入时刻；
 *       审批后两者都等于审批时刻。要精确历史需要另开迁移加两列，本轮按「保守手术」不动表结构。</li>
 *   <li>领域对象里的 {@code actualQty = -1} 表示「还没数」，库里存 NULL 更干净；读回时再还原成 -1，
 *       避免 0 和「没数」被混为一谈。</li>
 * </ol>
 */
@Repository
public class StockCheckRepositoryImpl implements StockCheckRepository {

    private final StockCheckMapper stockCheckMapper;

    public StockCheckRepositoryImpl(StockCheckMapper stockCheckMapper) {
        this.stockCheckMapper = stockCheckMapper;
    }

    @Override
    public void save(StockCheck check) {
        boolean approved = StockCheck.APPROVED.equals(check.getStatus());
        StockCheckPO head = stockCheckMapper.selectByCheckNo(check.getCheckNo());
        if (head == null) {
            head = new StockCheckPO();
            head.setCheckNo(check.getCheckNo());
            head.setStatus(check.getStatus());
            head.setOperatorId(check.getOperatorId());
            if (approved) {
                head.setApprovedBy(check.getOperatorId());
            }
            stockCheckMapper.insert(head);
        } else {
            head.setStatus(check.getStatus());
            if (approved) {
                head.setApprovedBy(check.getOperatorId());
            }
            stockCheckMapper.updateById(head);
        }

        Integer actualQty = check.getActualQty() < 0 ? null : (int) check.getActualQty();
        stockCheckMapper.upsertItem(head.getId(), check.getSkuId(), (int) check.getBookQty(),
                actualQty, (int) check.getDiffQty());
    }

    @Override
    public Optional<StockCheck> findByCheckId(String checkNo) {
        StockCheckPO head = stockCheckMapper.selectByCheckNo(checkNo);
        if (head == null) {
            return Optional.empty();
        }
        StockCheckItemPO item = stockCheckMapper.selectItemByCheckId(head.getId());
        if (item == null) {
            // 头在行不在属于脏数据（正常写入两表成对出现），按「查不到」处理而不是抛异常。
            return Optional.empty();
        }
        String status = head.getStatus();
        long updatedAt = TimeUtil.toMillis(head.getUpdatedAt());
        long recordedAt = StockCheck.DRAFT.equals(status) ? 0L : updatedAt;
        long approvedAt = StockCheck.APPROVED.equals(status) ? updatedAt : 0L;
        return Optional.of(StockCheck.rehydrate(
                head.getCheckNo(),
                item.getSkuId(),
                item.getBookQty() == null ? 0 : item.getBookQty(),
                item.getActualQty() == null ? -1L : item.getActualQty(),
                item.getDiffQty() == null ? 0 : item.getDiffQty(),
                status,
                head.getOperatorId() == null ? 0L : head.getOperatorId(),
                TimeUtil.toMillis(head.getCreatedAt()),
                recordedAt,
                approvedAt));
    }
}
